/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.test.lib.zink;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public abstract class Zink  implements Closeable
{

    /**
     * Create a Stream of ZRec records parsed from a ZIP file
     * @param path the path to the ZIP file
     * @return a Stream of ZRec records
     * @throws IOException if an I/O error occurs when opening the file
     */
    public static Stream<ZRec> stream(Path path) throws IOException {
        Zpliterator spliterator = new Zpliterator(Files.newByteChannel(path));
        Stream<ZRec> stream = StreamSupport.stream(spliterator, false);
        stream.onClose(() -> {
            try {
                spliterator.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return stream;
    }

    /**
     * Create a Stream of ZRec records parsed from an in-memory byte array
     * @param zip the byte array containing the ZIP
     * @return a Stream of ZRec records
     */
    public static Stream<ZRec> stream(byte[] zip) {
        return StreamSupport.stream(new Zpliterator(new ByteArrayChannel(zip)), false);
    }

    /**
     * Return a mapping function which transforms a regular ZIP stream into
     * a Zip64-formatted ZIP stream. This includes: updating the size and csize
     * fields of Loc and Cen headers; updating the dstart and locOff fields of
     * Cen headers; updating Desc fields to use 8-byte size and csize fields;
     * adding Zip64 extra fields to Loc and Cen headers and injecting Eoc64Rec
     * and Eoc64Loc records into the stream.
     * @return a Function which transforms the stream into the Zip64 format
     */
    public static Function<ZRec, Stream<? extends ZRec>> toZip64() {
        return r -> switch (r) {
            case Loc loc -> Stream.of(loc.toZip64());
            case Desc desc -> Stream.of(desc.toZip64());
            case Cen cen -> Stream.of(cen.toZip64());
            case Eoc eoc -> Stream.of(Eoc64Rec.of(eoc), Eoc64Loc.of(eoc), eoc.toZip64());
            default -> Stream.of(r);
        };
    }

    /**
     * Returns a builder for configuring a collector for a stream of ZIP records
     * @return a configurable collector
     */
    public static ZCollector collect() {
        return new ZCollector();
    }

    /**
     * Returns a collector which writes the ZIP stream to a file
     * @param path the path to the ZIP file to write to
     * @return a collector which writes the ZIP stream to a file
     */
    public static Collector<ZRec, Zink, Path> toFile(String path) {
        return toFile(Path.of(path));
    }

    /**
     * Returns a collector which writes the ZIP stream to a file
     * This method is equivalent to calling:
     * {@snippet lang=java :
     *   Zink.toFile(Path.of(path));
     * }
     * @param path the Path to the ZIP file to write to
     * @return a collector which writes the ZIP stream to a file
     */
    public static Collector<ZRec, Zink, Path> toFile(Path path) {
        return collect().toFile(path);
    }

    /**
     * Returns a collector which writes the ZIP stream to a byte array
     * @return a byte array containing the ZIP
     */
    public static Collector<ZRec, Zink, byte[]> toByteArray() {
        return collect().toByteArray();
    }

    /**
     * A builder containing methods to configure and create collectors for ZIP Streams
     */
    public static class ZCollector {
        private final boolean fixOffsets;
        private final Consumer<ZRec> trace;

        private ZCollector() {
            this(true, r -> {});
        }
        private ZCollector(boolean fixOffsets, Consumer<ZRec> trace) {
            this.fixOffsets = fixOffsets;
            this.trace = trace;
        }

        /**
         * Disable the default offset-fixing behaviour, allowing
         * invalid size or offset fields to be produced.
         * @return a ZCollector builder with offset-fixing disabled
         */
        public ZCollector disableOffsetFixing() {
            return new ZCollector(false, trace);
        }

        /**
         * Enable printing of a human readable trace of the collected ZIP stream
         * @return a ZCollector builder with tracing enabled
         */
        public ZCollector trace() {
            return new ZCollector(fixOffsets, new Trace(System.out, StandardCharsets.UTF_8));
        }

        /**
         * Returns a collector which writes the ZIP stream to a file.
         * @param path the Path of the ZIP file to write to
         * @return a collector which writes the ZIP stream to a file
         */
        public Collector<ZRec, Zink, Path> toFile(Path path) {
            return Zink.toFile(path, this);
        }

        /**
         * Returns a collector which writes the ZIP stream to a file.
         * This is equivalent to calling:
         * {@snippet lang=java :
         *   toFile(Path.of(path));
         * }
         * @param path the path of the ZIP file to write to
         * @return a collector which writes the ZIP stream to a file
         */
        public Collector<ZRec, Zink, Path> toFile(String path) {
            return toFile(Path.of(path));
        }

        /**
         * Returns a collector which writes the ZIP stream to a byte array.
         * @return a collector which writes the ZIP stream to a byte array.
         */
        public Collector<ZRec, Zink, byte[]> toByteArray() {
            return Zink.toByteArray(this);
        }
    }

    // Private implemetation members
    private static Collector<ZRec, Zink, Path> toFile(Path path, ZCollector collector) {
        try {
            return new ZinkCollector<Path>(new StreamZink(collector, new FileOutputStream(path.toFile()).getChannel())) {
                @Override
                public Function<Zink, Path> finisher() {
                    return (z) -> {
                        try {
                            z.close();
                            return path;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    };
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static Collector<ZRec, Zink, byte[]> toByteArray(ZCollector collector) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return new ZinkCollector<byte[]>(new StreamZink(collector, Channels.newChannel(out))) {
            @Override
            public Function<Zink, byte[]> finisher() {
                return (z) -> {
                    try {
                        z.close();
                        return out.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        };
    }

    /**
     * Return a ZRec stream combining the entries of first stream followed by the entries of the second stream
     * @param first the first stream
     * @param second the second stream
     * @return a stream with the entries of the first stream followed by the entries of the second stream
     */
    public static Stream<ZRec> concat(Stream<ZRec> first, Stream<ZRec> second) {

        ConcatZpliterator concatZpliterator = new ConcatZpliterator(first, second);

        return StreamSupport.stream(concatZpliterator, false);
    }

    private static class ConcatZpliterator extends Spliterators.AbstractSpliterator<ZRec> implements Consumer<ZRec> {

        private final Spliterator<ZRec> a;
        private final Spliterator<ZRec> b;
        private ZRec rec;
        private Cen aCen;
        private Cen bCen;
        private Eoc aEoc;
        private boolean aCenFound;
        private boolean bCenFound;
        private boolean aEocFound;
        private boolean bEocFound;

        ConcatZpliterator(Stream<ZRec> aStream, Stream<ZRec> bStream) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.DISTINCT |
                    Spliterator.IMMUTABLE | Spliterator.NONNULL);
            a = aStream.spliterator();
            b = bStream.spliterator();
        }

        @Override
        public boolean tryAdvance(Consumer<? super ZRec> action) {
            if (!aCenFound) {
                if (a.tryAdvance(this)) {
                    if (rec instanceof Cen cen) {
                        aCen = cen;
                        aCenFound = true;
                    } else {
                        action.accept(rec);
                        return true;
                    }
                } else {
                    return false;
                }
            }

            if (!bCenFound) {
                if (b.tryAdvance(this)) {
                    if (rec instanceof Cen cen) {
                        bCen = cen;
                        action.accept(aCen);
                        bCenFound = true;
                    } else {
                        action.accept(rec);
                    }
                    return true;
                } else {
                    return false;
                }
            }
            if (!aEocFound) {
                if (a.tryAdvance(this)) {
                    if (rec instanceof Eoc eoc) {
                        aEoc = eoc;
                        aEocFound = true;
                        action.accept(bCen);
                    } else {
                        action.accept(rec);
                    }
                    return true;
                } else {
                    return false;
                }
            }

            if (!bEocFound) {
                if (b.tryAdvance(this)) {
                    if (rec instanceof Eoc eoc) {
                        bEocFound = true;
                        Eoc merged = new Eoc(aEoc.thisDisk(),
                                aEoc.startDisk(),
                                (short) (aEoc.diskEntries() + eoc.diskEntries()),
                                (short) (aEoc.totalEntries() + eoc.totalEntries()),
                                aEoc.cenSize() + eoc.cenSize(),
                                (int) (aEoc.cenOffset() + eoc.cenOffset()),
                                aEoc.comment());
                        action.accept(merged);
                        return false;
                    } else {
                        action.accept(rec);
                        return true;
                    }
                } else {
                    return false;
                }
            }

            return false;
        }


        @Override
        public void accept(ZRec zRec) {
            rec = zRec;
        }
    }

    private abstract static class ZinkCollector<R> implements Collector<ZRec, Zink, R> {
        private final Zink zink;

        private ZinkCollector(Zink zink) {
            this.zink = zink;
        }

        @Override
        public Supplier<Zink> supplier() {
            return () -> zink;
        }

        @Override
        public BiConsumer<Zink, ZRec> accumulator() {
            return (z, rec) -> {
                try {
                    z.write(rec);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
        }

        @Override
        public BinaryOperator<Zink> combiner() {
            return (z, z2) -> z;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }

    abstract void write(ZRec record) throws IOException;

    private static class StreamZink extends Zink {

        private final WritableByteChannel out;
        private final Consumer<ZRec> trace;
        private long written = 0L;
        private long offset;

        private final Function<ZRec, ZRec> offsetFixer;

        StreamZink(ZCollector collector, WritableByteChannel out) {
            this.out = out;
            this.offsetFixer = collector.fixOffsets ? new OffsetFixer() : Function.identity();
            this.trace = collector.trace;
        }

        private int write(byte[] data) throws IOException {
            return write(ByteBuffer.wrap(data));
        }

        private int write(ByteBuffer data) throws IOException {
            //int written = out.write(data);
            this.written += written;
            return 0;
        }

        @Override
        public void close() throws IOException {
            out.close();
        }

        @Override
        void write(ZRec rec) throws IOException {

            if (out instanceof SeekableByteChannel seek) {
                long location = seek.position();
                if (this.offset != location) {
                    throw new IllegalStateException("Offset does not match location");
                }
            }
            offset += rec.sizeOf();

            ZRec offsetAdjusted = offsetFixer.apply(rec);
            trace.accept(offsetAdjusted);
            switch (offsetAdjusted) {
                case Loc loc -> loc.write(out);
                case FileData data -> data.write(out);
                case Desc desc -> desc.write(out);
                case Cen cen -> cen.write(out);
                case Eoc64Rec eocRec -> eocRec.write(out);
                case Eoc64Loc eocLoc -> eocLoc.write(out);
                case Eoc eoc -> eoc.write(out);
                case Skip sb -> sb.write(out);
            }
        }
    }

    private static class ByteArrayChannel implements SeekableByteChannel {
        private final byte[] bytes;
        private int position;
        private boolean closed;

        ByteArrayChannel(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int len = Math.min(remaining(), dst.remaining());
            dst.put(bytes, position, len);
            position += len;
            return len;
        }

        private int remaining() {
            return bytes.length - position;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new IllegalStateException("Not implemented");
        }

        @Override
        public long position() throws IOException {
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if(newPosition > bytes.length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            position = (int) newPosition;
            return this;
        }

        @Override
        public long size() throws IOException {
            return bytes.length;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            throw new IllegalStateException("Not implemented");
        }
    }

    private static class SeekableByteChannelWriter implements FileData.Writer {

        private final SeekableByteChannel input;
        private final long off;
        private final long len;

        SeekableByteChannelWriter(SeekableByteChannel input, long off, long len) {
            this.input = input;
            this.off = off;
            this.len = len;
        }
        @Override
        public void write(WritableByteChannel out) throws IOException {
            long orig = input.position();
            input.position(off);
            try {
                int size = 512;
                if (len < size) {
                    size = (int) len;
                }
                ByteBuffer buf = ByteBuffer.allocate(size);
                long rem = len;
                while (rem > 0) {
                    int len = buf.capacity();
                    if (rem < len) {
                        len = (int) rem;
                    }
                    buf.limit(len).rewind();
                    int read = input.read(buf);
                    buf.flip();
                    out.write(buf);
                    rem -= read;
                }
            } finally {
                input.position(orig);
            }
        }
    }

    static byte[] getBytes(ReadableByteChannel channel, int len) throws IOException {
        byte[] bytes = new byte[len];
        channel.read(ByteBuffer.wrap(bytes));
        return bytes;
    }

    static LocalDate getLocalDate(short date) {
        int year = (date >> 9) &  0x7F;
        year += 1980;

        int month = (date >> 5) & 0xF;

        int dom = date & 0x1F;

        return LocalDate.of(year, month, dom);

    }

    static LocalDateTime getLocalDateTime(LocalDate date, LocalTime time) {
        return date.atTime(time);
    }

    static ExtField[] parseExt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        List<ExtField> fields = new ArrayList<>();
        while (buffer.remaining() > 0) {
            short id = buffer.getShort();
            short dsize = buffer.getShort();

            if (id == ExtZip64.ID) { // Zip64 sizes
                fields.add(ExtZip64.read(dsize, buffer));
            } else if (id == ExtTs.ID) { // Extended timestamp
                fields.add(ExtTs.read(dsize, buffer));
            } else if (id == ExtWinNT.ID) { // Extended timestamp
                fields.add(ExtWinNT.read(buffer));
            } else {
                byte[] data = new byte[dsize];
                buffer.get(data);
                fields.add(new ExtGeneric(id, dsize, data));
            }


        }
        return fields.toArray(ExtField[]::new);
    }
    static LocalTime getLocalTime(short time) {
        int hours = (time >> 11) & 0x1F;
        int minutes = (time >> 5) & 0x3F;
        int secs = 2 * (time & 0x1F);
        return LocalTime.of(hours, minutes, secs);
    }

    private static class Zpliterator extends Spliterators.AbstractSpliterator<ZRec> {
        private final SeekableByteChannel channel;
        private States state;
        private Loc loc;

        private long cenIndex;
        private long offset = 0;
        private final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        private Inflater inflater;
        private ByteBuffer readBuf;
        private ByteBuffer writeBuf;

        public void close() throws IOException {
            channel.close();
            inflater.end();
        }

        enum States {
            SIG, FILE_DATA, DATA_DESC
        }

        public Zpliterator(SeekableByteChannel channel) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.DISTINCT |
                    Spliterator.IMMUTABLE | Spliterator.NONNULL);
            this.state = States.SIG;
            this.channel = channel;
        }

        @Override
        public boolean tryAdvance(Consumer<? super ZRec> action) {
            try {
                if (channel.position() >= channel.size()) {
                    return false;
                }
                ZRec next = parseNext();
                long nextOffset = offset + next.sizeOf();
                if (channel instanceof SeekableByteChannel sbc) {
                    long position = sbc.position();
                    if (position != nextOffset) {
                        throw new IllegalStateException("Unexcpected offset");
                    }
                }
                offset = nextOffset;
                action.accept(next);
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private ZRec parseNext() throws IOException {
            return switch (state) {
                case SIG -> readSig();
                case FILE_DATA -> {
                    if ((loc.flags() & 8) != 0) {
                        state = States.DATA_DESC;
                    } else {
                        state = States.SIG;
                    }
                    yield readData();
                }
                case DATA_DESC -> {
                    state = States.SIG;

                    boolean zip64 = loc.isZip64();

                    int sigOrCrc = getInt();

                    if (sigOrCrc == Desc.SIG) {
                        yield Desc.read(channel, sigOrCrc, true, zip64);
                    } else {
                        yield Desc.read(channel, sigOrCrc, false, zip64);
                    }
                }
            };
        }

        private int getInt() throws IOException {
            buffer.clear().limit(Integer.BYTES);
            channel.read(buffer);
            return buffer.getInt(0);
        }

        private boolean isLocOrCenSig(int number) {
            return number == Loc.SIG || number == Cen.SIG;
        }

        private FileData readData() throws IOException {
            long start = channel.position();

            if (loc.method() == Loc.DEFLATE) {

                try {
                    // Lazy init of inflater, read and write buffers
                    if (inflater == null) {
                        inflater = new Inflater(true);
                    } else {
                        inflater.reset();
                    }
                    if (readBuf == null) {
                        readBuf = ByteBuffer.allocate(1024);
                    }
                    if (writeBuf == null) {
                        writeBuf = ByteBuffer.allocate(1024);
                    }

                    int n;
                    while (!inflater.finished() && !inflater.needsDictionary()) {
                        do {
                            writeBuf.clear();
                            if (inflater.finished() || inflater.needsDictionary()) {
                                break;
                            }
                            if (inflater.needsInput()) {
                                readBuf.clear();
                                int len = channel.read(readBuf);
                                if (len == -1) {
                                    throw new EOFException("Unexpected end of ZLIB input stream");
                                }
                                readBuf.flip();
                                inflater.setInput(readBuf);
                            }
                        } while ((n = inflater.inflate(writeBuf)) == 0);
                    }

                    long csize = inflater.getBytesRead();
                    long size = inflater.getBytesWritten();
                    channel.position(start + csize);
                    return new FileData(new SeekableByteChannelWriter(channel, (int) start, csize), csize);
                } catch (DataFormatException e) {
                    throw new IOException(e.getMessage(), e);
                }


            } else {
                long size = loc.isZip64() ? loc.extra(ExtZip64.class).get().csize() : loc.csize();
                channel.position(start + size);
                return new FileData(new SeekableByteChannelWriter(channel, (int) start, size), size);
            }
        }
        private ZRec readSig() throws IOException {
            int sig = getInt();

            switch (sig) {
                case Loc.SIG:
                    loc = Loc.read(channel);
                    state = States.FILE_DATA;
                    return loc;
                case Cen.SIG:
                    return Cen.read(channel);
                case Eoc.SIG:
                    return Eoc.read(channel);
                case Eoc64Rec.SIG:
                    return Eoc64Rec.read(channel);
                case Eoc64Loc.SIG:
                    return Eoc64Loc.read(channel);
                default: throw new IllegalArgumentException("Unknown sig: " + Integer.toHexString(sig));
            }
        }
    }

    private static class OffsetFixer implements Function<ZRec, ZRec> {
        long offset;
        List<Long> locOffsets = new ArrayList<>();
        int cenIdx = 0;
        long cenOffset;
        private long eoc64Off;
        private long cenSize = 0;
        @Override
        public ZRec apply(ZRec rec) {
            long currentOffset = offset;
            offset += rec.sizeOf();
            return switch (rec) {
                case Loc loc -> {
                    // Record offset of each Loc
                    locOffsets.add(currentOffset);
                    yield loc;
                }
                case Cen cen -> {
                    if (cenIdx == 0) { // First
                        cenOffset = currentOffset;
                    }
                    // Look up offset of corresponding Loc
                    long locOff = locOffsets.get(cenIdx++);
                    // Adjust the CEN's loc offset
                    if (cen.locOff() == Cen.ZIP64_SIZE_32) {
                        ExtField[] extra = Stream.of(cen.extra())
                                .map(r ->
                                        switch (r) {
                                            case ExtZip64 e -> e.locOff(locOff);
                                            default -> r;
                                        }).toArray(ExtField[]::new);
                        yield cen.extra(extra);
                    } else {
                        yield cen.locOff((int) locOff);
                    }
                }
                case Eoc64Rec eoc64 -> {
                    // Record offset of this record
                    eoc64Off = currentOffset;
                    // Record CEN size
                    cenSize = currentOffset - cenOffset;
                    // Adust CEN size and offset
                    yield eoc64.cenOff(cenOffset).cenSize(cenSize)
                            .totalEntries(cenIdx);
                }
                case Eoc64Loc eoc64Loc -> {
                    yield eoc64Loc.eocOff(eoc64Off);
                }
                case Eoc eoc when !eoc.isZip64() -> {
                    if (cenSize == 0) { // No Eoc64Rec
                        cenSize = currentOffset - cenOffset;
                    }
                    // Adust CEN size and offset
                    yield eoc.cenOffset((int) cenOffset)
                            .cenSize((int) cenSize)
                            .diskEntries((short) cenIdx)
                            .totalEntries((short) cenIdx);
                }
                default -> rec;
            };
        }
    }
    private static class Trace implements Consumer<ZRec> {
        private final PrintStream pw;
        private final Charset charset;
        private long offset;

        Trace(PrintStream pw, Charset charset) {
            this.pw = pw;
            this.charset = charset;
        }

        Trace(PrintStream pw) {
            this.pw = pw;
            this.charset = StandardCharsets.UTF_8;
        }

        @Override
        public void accept(ZRec rec) {
            long currentOffset = offset;
            switch (rec) {
                case  Loc loc -> {
                    header("Local File Header");
                    row("signature", "0x%08x".formatted(Loc.SIG), Integer.BYTES);
                    row("version", "%d".formatted(loc.version()), Short.BYTES);
                    row("flags", "0x%04x".formatted(loc.flags()), Short.BYTES);
                    row("method", "%d".formatted(loc.method()), Short.BYTES, method(loc.method()));
                    printTimeAndDate(loc.time(), loc.date(), 6);
                    row("crc", "0x%08x".formatted(loc.crc()), Integer.BYTES);
                    row("csize", "%d".formatted(loc.csize()), Integer.BYTES);
                    row("size", "%d".formatted(loc.size()), Integer.BYTES);
                    row("nlen", "%d".formatted(loc.nlen()), Short.BYTES);
                    row("elen", "%d".formatted(loc.elen()), Short.BYTES);
                    row("name", "%d bytes".formatted(loc.name().length),
                            loc.name().length,
                            "'" + new String(loc.name(), charset) +"'");
                    printExtFields(loc.extra());
                }
                case FileData data -> {
                    header("File Data");
                    row("data", "%d bytes".formatted(data.size()), data.size());
                }
                case Desc desc -> {
                    header("Data Descriptor");
                    if (desc.signed()) {
                        row("signature", "0x%08x".formatted(Desc.SIG), Integer.BYTES);
                    }
                    printDesc(desc);
                }
                case Cen cen -> {
                    header("Central Directory File Header");
                    row("signature", "0x%08x".formatted(Cen.SIG), Integer.BYTES);
                    row("made by version", "%d".formatted(cen.version()), Short.BYTES);
                    row("extract version", "%d".formatted(cen.extractVersion()), Short.BYTES);
                    row("flags", "0x%04x".formatted(cen.flags()), Short.BYTES);
                    row("method", "%d".formatted(cen.method()), Short.BYTES, method(cen.method()));
                    printTimeAndDate(cen.time(), cen.date(), 8);
                    row("crc", "0x%08x".formatted(cen.crc()), Integer.BYTES);
                    row("csize", "%d".formatted(cen.csize()), Integer.BYTES);
                    row("size", "%d".formatted(cen.size()), Integer.BYTES);
                    row("diskstart", "%d".formatted(cen.diskStart()), Short.BYTES);
                    row("nlen", "%d".formatted(cen.nlen()), Short.BYTES);
                    row("elen", "%d".formatted(cen.elen()), Short.BYTES);
                    row("clen", "%d".formatted(cen.clen()), Short.BYTES);
                    row("iattr", "0x%02x".formatted(cen.internalAttr()), Short.BYTES);
                    row("eattr", "0x%04x".formatted(cen.externalAttr()), Integer.BYTES);
                    row("loc offset", "%d".formatted(cen.locOff()), Integer.BYTES);
                    row("name", "%d bytes".formatted(cen.name().length),
                            cen.name().length,
                            "'" + new String(cen.name(), charset) + "'");

                    printExtFields(cen.extra());
                    if (cen.comment().length > 0) {
                        row("comment", "%d bytes".formatted(cen.comment().length),
                                cen.comment().length,
                                "'" + new String(cen.comment(), charset) + "'");
                    }

                }
                case Eoc64Rec erec -> {
                    header("Zip64 End of Central Directory Record");
                    row("signature", "0x%08x".formatted(Eoc64Rec.SIG), Integer.BYTES);
                    row("record size", "%d".formatted(erec.size()), Long.BYTES);
                    row("made by version", "%d".formatted(erec.version()), Short.BYTES);
                    row("extract version", "%d".formatted(erec.extractVersion()), Short.BYTES);
                    row("this disk", "%d".formatted(erec.thisDisk()), Integer.BYTES);
                    row("cen disk", "%d".formatted(erec.startDisk()), Integer.BYTES);
                    row("entries", "%d".formatted(erec.numEntries()), Long.BYTES);
                    row("total entries", "%d".formatted(erec.totalEntries()), Long.BYTES);
                    row("cen size", "%d".formatted(erec.cenSize()), Long.BYTES);
                    row("cen offset", "%d".formatted(erec.cenOff()), Long.BYTES);
                    printExtFields(erec.extra());
                }
                case Eoc64Loc eloc -> {
                    header("Zip64 End of Central Directory Locator");
                    row("signature", "0x%08x".formatted(Eoc64Loc.SIG), Integer.BYTES);
                    row("eoc disk", "%d".formatted(eloc.eocDisk()), Integer.BYTES);
                    row("eoc offset", "%d".formatted(eloc.eocOff()), Long.BYTES);
                    row("total disks", "%d".formatted(eloc.totalDisks()), Integer.BYTES);
                }
                case Eoc eoc -> {
                    header("End of Central Directory");
                    row("signature", "0x%08x".formatted(Eoc.SIG), Integer.BYTES);
                    row("this disk", "%d".formatted(eoc.thisDisk()), Short.BYTES);
                    row("cen disk", "%d".formatted(eoc.startDisk()), Short.BYTES);
                    row("entries disk", "%d".formatted(eoc.diskEntries()), Short.BYTES);
                    row("entries total", "%d".formatted(eoc.totalEntries()), Short.BYTES);
                    row("cen size", "%d".formatted(eoc.cenSize()), Integer.BYTES);
                    row("cen offset", "%d".formatted(eoc.cenOffset()), Integer.BYTES);
                    row("clen", "%d".formatted(eoc.clen()), Short.BYTES);
                    if (eoc.comment().length > 0) {
                        row("comment", "%d bytes".formatted(eoc.comment().length),
                                eoc.comment().length,
                                "'" + new String(eoc.comment(), charset) + "'");
                    }
                }
                case Skip skip -> {
                    header("File hole");
                    row("cen offset", "%d".formatted(skip.skip()), skip.skip());
                }
            }
            pw.println();
            if (offset != currentOffset + rec.sizeOf()) {
                throw new IllegalStateException("offset != currentOffset + recordSize");
            }
        }

        private void printDesc(Desc desc) {
            row("crc", "0x%08x".formatted(desc.crc()), Integer.BYTES);
            row("csize", "%d".formatted(desc.csize()), desc.zip64() ? Long.BYTES : Integer.BYTES);
            row("size", "%d".formatted(desc.size()), desc.zip64() ? Long.BYTES : Integer.BYTES);
        }

        private void printTimeAndDate(short time, short date, int pad) {
            row("time", "0x%02x".formatted(time), Short.BYTES, getLocalTime(time).toString());
            row("date", "0x%02x".formatted(date), Short.BYTES, getLocalDate(date).toString());
        }

        private void printExtFields(ExtField[] extFields) {
            for (ExtField e : extFields) {
                row("ext id", "0x%04x".formatted(e.id()), Short.BYTES, extType(e.id()));
                row("ext size", "%d".formatted(e.data().length), Short.BYTES);
                switch (e ) {
                    case ExtZip64 zip64 -> {
                        printZip64(zip64);
                    }
                    case ExtTs ts -> {
                        printExtTs(ts);
                    }
                    case ExtWinNT winNt -> {
                        printWinNT(winNt);
                    }
                    default -> {
                        byte[] data = e.data();
                        row("ext data", "%d bytes".formatted(data.length), data.length, HexFormat.ofDelimiter(" ").formatHex(data));
                    }
                }
            }
        }

        private void printZip64(ExtZip64 zip64) {
            if (zip64.dsize() >= 8) {
                row("z64 size", "%d".formatted(zip64.size()), Long.BYTES);
            }
            if (zip64.dsize() >= 16) {
                row("z64 csize", "%d".formatted(zip64.csize()), Long.BYTES);
            }
            if (zip64.dsize() >= 24) {
                row("z64 locoff", "%d".formatted(zip64.locOff()), Long.BYTES);
            }
            if (zip64.dsize() == 28) {
                row("z64 diskStart", "%d".formatted(zip64.diskStart()), Integer.BYTES);
            }
        }
        private void printExtTs(ExtTs ts) {
            row("ts flag", "0x%02x".formatted(ts.flag()), Byte.BYTES);
            if (ts.modtime() != -1) {
                row("ts ModTime", "%d".formatted(ts.modtime()), Integer.BYTES,
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(ts.modtime()), ZoneOffset.UTC.normalized()).toString());
            }
            if (ts.actime() != -1) {
                row("ts AcTime", "%d".formatted(ts.actime()), Integer.BYTES,
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(ts.modtime()), ZoneOffset.UTC.normalized()).toString());
            }
            if (ts.crtime() != -1) {
                row("ts CrTime", "%d".formatted(ts.crtime()), Integer.BYTES,
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(ts.modtime()), ZoneOffset.UTC.normalized()).toString());
            }
        }

        private void printWinNT(ExtWinNT ts) {
            row("wnt res", "0x%x".formatted(ts.reserved()), Integer.BYTES);
            row("wnt tag", "0x%x".formatted(ExtWinNT.TIMETAG), Short.BYTES);
            row("wnt size", "%d".formatted(ExtWinNT.TIMETAG), Short.BYTES);
            row("wnt mtime", "%d".formatted(ts.mtime()), Long.BYTES);
            row("wnt atime", "%d".formatted(ts.atime()), Long.BYTES);
            row("wnt ctime", "%d".formatted(ts.ctime()), Long.BYTES);
        }

        private void header(String name) {
            pw.printf("------  %s  ------%n", name);
        }

        private void row(String name, String value, long size) {
            row(name, value, size, "");
        }

        private void row(String name, String value, long size, String comment) {
            pw.printf("%06d  %-18s %-15s%s%n", offset, name, value, comment);
            offset += size;
        }

        private String extType(short id) {
            return switch (((int)id) & 0xFFFF) {
                case 0x0001 -> "Zip64 extended information extra field";
                case 0x0007 -> "AV Info";
                case 0x0008 -> "Reserved for extended language encoding data (PFS)";
                case 0x0009 -> "OS/2";
                case 0x000a -> "NTFS (Win9x/WinNT FileTimes)";
                case 0x000c -> "OpenVMS";
                case 0x000d -> "UNIX";
                case 0x000e -> "Reserved for file stream and fork descriptors";
                case 0x000f -> "Patch Descriptor";
                case 0x0014 -> "PKCS#7 Store for X.509 Certificates";
                case 0x0015 -> "X.509 Certificate ID and Signature for individual file";
                case 0x0016 -> "X.509 Certificate ID for Central Directory";
                case 0x0017 -> "Strong Encryption Header";
                case 0x0018 -> "Record Management Controls";
                case 0x0019 -> "PKCS#7 Encryption Recipient Certificate List";
                case 0x0020 -> "Reserved for Timestamp record";
                case 0x0021 -> "Policy Decryption Key Record";
                case 0x0022 -> "Smartcrypt Key Provider Record";
                case 0x0023 -> "Smartcrypt Policy Key Data Record";
                case 0x0065 -> "IBM S/390 (Z390), AS/400 (I400) attributes - uncompressed";
                case 0x0066 -> "Reserved for IBM S/390 (Z390), AS/400 (I400) attributes - compressed";
                case 0x4690 -> "POSZIP 4690 (reserved)";
                case 0x07c8 -> "Macintosh";
                case 0x2605 -> "ZipIt Macintosh";
                case 0x2705 -> "ZipIt Macintosh 1.3.5+";
                case 0x2805 -> "ZipIt Macintosh 1.3.5+";
                case 0x334d -> "Info-ZIP Macintosh";
                case 0x4341 -> "Acorn/SparkFS";
                case 0x4453 -> "Windows NT security descriptor (binary ACL)";
                case 0x4704 -> "VM/CMS";
                case 0x470f -> "MVS";
                case 0x4b46 -> "FWKCS MD5 (see below)";
                case 0x4c41 -> "OS/2 access control list (text ACL)";
                case 0x4d49 -> "Info-ZIP OpenVMS";
                case 0x4f4c -> "Xceed original location extra field";
                case 0x5356 -> "AOS/VS (ACL)";
                case 0x5455 -> "Extended timestamp";
                case 0x554e -> "Xceed unicode extra field";
                case 0x5855 -> "Info-ZIP UNIX (original, also OS/2, NT, etc)";
                case 0x6375 -> "Info-ZIP Unicode Comment Extra Field";
                case 0x6542 -> "BeOS/BeBox";
                case 0x7075 -> "Info-ZIP Unicode Path Extra Field";
                case 0x756e -> "ASi UNIX";
                case 0x7855 -> "Info-ZIP UNIX (new)";
                case 0x7875 -> "Info-ZIP UNIX (newer UID/GID)";
                case 0xa11e -> "Data Stream Alignment (Apache Commons-Compress)";
                case 0xa220 -> "Microsoft Open Packaging Growth Hint";
                case 0xfd4a -> "SMS/QDOS";
                case 0x9901 -> "AE-x encryption structure (see APPENDIX E)";
                case 0x9902 -> "unknown";
                default     -> "Unknown extended field";
            };
        }

        private String method(short method) {
            return switch (method) {
                case 0 -> "Stored (no compression)";
                case 1 -> "Shrunk";
                case 2 -> "Reduced with compression factor 1";
                case 3 -> "Reduced with compression factor 2";
                case 4 -> "Reduced with compression factor 3";
                case 5 -> "Reduced with compression factor 4";
                case 6 -> "Imploded";
                case 7 -> "Reserved for Tokenizing compression algorithm";
                case 8 -> "Deflated";
                case 9 -> "Enhanced Deflating using Deflate64(tm)";
                case 10 -> "PKWARE Data Compression Library Imploding (old IBM TERSE)";
                case 11 | 13 | 15 | 17 -> "Reserved by PKWARE";
                case 12 -> "BZIP2";
                case 14 -> "LZMA";
                case 16 -> "IBM z/OS CMPSC Compression";
                case 18 -> "IBM TERSE (new)";
                case 19 -> "IBM LZ77 z Architecture";
                case 20 -> "deprecated (use method 93 for zstd)";
                case 93 -> "Zstandard (zstd) Compression";
                case 94 -> "MP3";
                case 95 -> "XC";
                case 96 -> "JPEG variant";
                case 97 -> "WavPack";
                case 98 -> "PPMd version I, Rev 1";
                case 99 -> "AE-x encryption marker ";
                default -> "Unknown compression method";
            };
        }
    }
}
