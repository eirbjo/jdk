# Zink: A type-safe test library for streaming ZIP transformations

## Summary

Introduce a test library capable of creating invalid or unusual ZIP files in the
OpenJDK test suites without leaking ZIP format details into the tests.

## Motivation

OpenJDK includes a suite of tests aiming to validate implementation classes in
`java.util.zip` and `jdk.nio.zipfs` packages. To exercise constraint 
verification code paths in these implementations, tests need to create ZIP files
which are syntactically or semantically invalid, or otherwise have a shape not easilly 
reproduced using the standard ZipOutputStream producer facility. Examples include 
out-of-bounds length or offset field values, invalid UTF byte sequences in names and
comments, or the use of Zip64 extra fields in small ZIP files. 

To produce such test vectors, current tests typically include hard-coded or manually 
calculated offset resolution, magic constant values, direct manipulation of ZIPs in
byte arrays and manual handling of little-endian byte order of fields.

This makes tests hard to read since the purpose of the test tends to drown in all the 
ZIP format details required to create the test vectors. Additionally, the binary nature
of ZIP files does not allow for easy inspection or comparison of test vectors, given 
the lack of human readability. Some tests must run manually because they consume large
amounts of CPU, memory and disk resources to produce huge ZIP files.

## Goals

- Introduce a test library to help create invalid or unusual ZIP test vectors without 
  internal ZIP file format details leaking into the tests.
- Introduce type-safe, immutable record classes for each of the main headers and records in the ZIP format.
- Expose ZIP contents as a `java.util.stream.Stream` of such records, allowing
  transformations on ZIP files to be expressed using `Stream` operations on immutable records. 
- Allow ZIP streams to be created from in-memory byte arrays or files.
- Allow ZIP streams to be collected into in-memory byte arrays or files.
- Allow ZIP streams to be traced in a human-readable, disassembled text format.
- Allow the production of sparse ZIP files (with 'empty holes' in them) for use by tests requiring multi-GB file sizes 
- Keep the library surface minimal and the implementation size reasonably small.
- Library users should not need to consider offset- and size-related field being invalidated
  by the introduction, deletion of change of records sizes. The library should transparently track 
  and update such fields as necessary.

## Non-goals

- It is not a goal to provide a full implementation of the ZIP format, including encryption 
  support, support for exotic compression methods etc.
- Performance should be reasonable for test purposes. It is not a goal to create an optimal implementation.
- It is not a goal to implement parsing of extra fields which are not in use by the OpenJDK
  implementation classes.
- While the library may provide some syntactic sugar for common and useful transformation  
  primitives, it is not a goal to provide this for every conceivable transformation.

## Description

### Records
The various ZIP file format headers ([Loc](Loc.java), [Cen](Cen.java), [Eoc](Eoc.java), ..) are implemented as a set of Java records
implementing the [ZRec](ZRec.java) sealed interface.

### Producing ZIP streams
The main library is implemented in a single abstract class [Zink](Zink.java). This class provide factories
for creating ZIP `Stream` instances from byte arrays or files:

```java
byte[] bytes = ...;
Stream<ZRec> stream = Zink.stream(bytes);               // Produce a stream from a byte array
Stream<ZRec> stream = Zink.stream(Path.of("file.zip")); // Produce a stream from a file
```

### Collecting streams
Similarly, `Zink` contains methods to collect streams into files or byte arrays:

```java
byte[] zip = stream.collect(Zink.toByteArray());
Path fileFile = stream.collect(Zink.toFile("file.zip"));
```
### Transforming streams

By chaining the factory and collection methods, we can produce an identity transform 
on the ZIP file:

```java
byte[] zip = ...
byte[] zip2 = Zink.stream(zip).collect(Zink.toByteArray());

asssertEquals(zip, zip2);
```

Real-life transformations can be implemented using `Stream` operations with
pattern matching:

```java
Zink.stream(smallZip())
        .map(r -> switch (r) {
            case Eoc eoc -> eoc.cenOffset(Integer.MAX_VALUE);
            default -> r;
        })
        .collect(Zink.toByteArray();
```

More complex operations which introduce new records may be implemeted using `flatMap` operations:

```java
Zink.stream(smallZip())
        .flatMap(Zink.toZip64()) // Transform to the Zip64 format
        .collect(Zink.toByteArray());
```

### Concatenating streams
Streams may be concatenated, forming a new stream with the contents of the first stream
followed by the contents of the second stream:

```java
Path concat = Zink.concat(
        Zink.stream(zipWithEntries("a", "b")),
        Zink.stream(zipWithEntries("c", "d"))
).collect(Zink.toFile("a-b-c-d.zip"));
```

### Offset and size calculations

The collectors keeps track of changes to the file size and structure and
automatically updates any affected offset or size-related field. If needed, this 
can be disabled by configuring the collector:

```java
stream.collect(Zink.collect()
                   .disableOffsetFixing() // Avoid fixing invalid CEN size
                   .toByteArray());
```

### Large, sparse files

The synthetic record [Skip](Skip.java) can be injected into a stream to produce sparse ZIP 
files with large 'holes' in them:

```java
Path zip = Zink.stream(template)
                .flatMap(Eoc.flatMap(eoc ->
                        Stream.of(Skip.of(padding), eoc.cenSize(adjustedCenSize)))
                ).collect(Zink.toFile("cen-size-too-large.zip"));
```

### Human-readable traces
A human readable trace may be printed during collection:

```
stream.collect().trace().toByteArray();
```

The following is a trace showing the structure of a single-entry ZIP file, including 
offsets of each field and record:
```
------  Local File Header  ------
000000  signature          0x04034b50     
000004  version            20             
000006  flags              0x0808         
000008  method             8              Deflated
000010  time               0x70a7         14:05:14
000012  date               0x565c         2023-02-28
000014  crc                0x00000000     
000018  csize              0              
000022  size               0              
000026  nlen               5              
000028  elen               0              
000030  name               5 bytes        'entry'

------  File Data  ------
000035  data               7 bytes        

------  Data Desciptor  ------
000042  signature          0x08074b50     
000046  crc                0x3610a686     
000050  csize              7              
000054  size               5              

------  Central Directory File Header  ------
000058  signature          0x02014b50     
000062  made by version    20             
000064  extract version    20             
000066  flags              0x0808         
000068  method             8              Deflated
000070  time               0x70a7         14:05:14
000072  date               0x565c         2023-02-28
000074  crc                0x3610a686     
000078  csize              7              
000082  size               5              
000086  diskstart          0              
000088  nlen               5              
000090  elen               0              
000092  clen               9              
000094  iattr              0x00           
000096  eattr              0x0000         
000100  loc offset         0              
000104  name               5 bytes        'entry'
000109  comment            9 bytes        'A comment'

------  End of Central Directory  ------
000118  signature          0x06054b50     
000122  this disk          0              
000124  cen disk           0              
000126  entries disk       1              
000128  entries total      1              
000130  cen size           60             
000134  cen offset         58             
000138  clen               0 
```

## Sample use

The following illustrates how the Zink library may be used to verify that ZIP files 
with an excessive CEN size in the End of central directory record is rejected with a ZipException:

```java
@Test
public void shouldRejectInvalidCenSize() throws IOException {

    // Modify the Eoc such that its CEN size exceeds the file size
    Path zip = Zink.stream(smallZip())
            .map(Eoc.map(eoc -> eoc.cenSize(MAX_CEN_SIZE)))
            .collect(Zink.collect()
                    .disableOffsetFixing() // Avoid fixing invalid CEN size
                    .toFile("cen-size-invalid.zip"));

    // Should reject ZIP because the CEN size is > file size
    ZipException ex = expectThrows(ZipException.class, () -> {
        readZip(zip);
    });

    assertEquals(ex.getMessage(), "invalid END header (bad central directory size)");
}
```
## Risks

The new library is in itself a body of code which needs to be maintained, understood and 
which may contain bugs. This increase in size and complexity could probably be offset against
a corresponding reduction of code size and complexity in current and future tests.

The library may need to be updated if the ZIP file format has significant updates in the future.

## Alternatives

Rather than introducing a new library, we could continue adding new tests which expose 
ZIP format internals. Efforts could be made to limit this exposure by using shared constants. 
Readability of existing tests could be improved by adding more code comments explaining 
the format. These would however invariably need to be repeated and spread across tests.

The library could be designed with a different paradigm and allow direct mutation of file
contents. An earlier iteration of this API included some experiments in this direction, but
after writing some actual sample tests, we believe that modelling the API as transformations
on streams of immutable records provide cleaner and safer test code. 
