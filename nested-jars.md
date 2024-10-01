# Support nested JAR files on the class and module paths

## Summary

Enhance the JDK to support loading "single jar" applications, where JAR files
on the class path or module path are packaged as entries nested within a larger 
JAR file bundles.


## Goals

1. Ehance `java.util.zip.ZipFile` with a new constructor allowing `ZipFile` to read
   ZIP data from an entry nested within a larger ZIP file. 
2. Ehance `java.util.jar.JarFile` with a new constructor allowing `JarFile` to read
   JAR data from an entry nested within a larger ZIP file.
3. Enhance the `jar` URL scheme to support nested URLs such as `jar:file:single.jar!/library.jar!/Library.class`
4. Enhance `java.net.URLClassLoader`'s parsing of the `Class-Path` JAR manifest
   attribute to support referring to nested JAR files
5. Enhance `java.lang.ModuleFinder` to find modules located in nested JARs packaged within a larger module JAR
6. Enhance `jlink` to support builing runtime images from single-jar modules on the `--module-path` 

## Non-Goals

1. It is not to change `javac` to allow nested jar files being used as libraries on the 
   class or module path.
2. It is not a goal to support arbitrary levels of nesting in `URLClassLoader`, 
   `ModuleFinder` or `jlink`. A single level of nesting is sufficient to support the 
   application packaging proposed here. 

## Motivation

A popular way to package Java applications is to bundle the
application along with its dependencies into a single JAR file. This provides a 
simple, convenient and platform independent way to distribute applications. 

The `maven-shade-plugin` does this by unpacking resources from each dependency
JAR into the single JAR file bundle. This causes challenges when information from
each JAR file needs to be merged into a single manifest, when some JAR files are 
multi-release while others are not, when service catalogs need merging or when some
JAR files are signed.

Spring Boot takes a different approach where each dependency is packaged as 
a STORED entry within the single JAR. Spring Boot then reimplements much of the
CEN parsing logic from `java.util.ZipFile` and also needs to reimplement 
`JarFile` features such as multi-release JARs and verification of signed JARs 
for feature parity with the JDK. A custom laucher class is also needed to load 
the application using the custom JAR loading infrastructure.

Spring Boot pays a significant performance cost for this. A prototype of the changes
proposed here shows ~15% reduced startup cost with Spring Petclinic packaged
as a single-jar.

Applications using the Java module system face similar challanges, since the 
JDK does not support finding modules nested within other JAR module files.

The problems mentioned above would be solved if we could teach the JDK how to
load applications, libraries and modules from JAR files packaged within other 
JAR files. Applications bundled as single-jar files would then get feature parity
and predictable performance compared with existing class- and module paths in the JDK. 


## Description

### ZipFile and JarFile support for nested ZIP/JAR files 
The current ZIP parsing logic implemented in `java.util.zip.ZipFile` works
by first reading the end of the ZIP file where it tries to locate the END record. It 
then skips backwards in the file to locate the CEN records. This allows `ZipFile`
to be lenient in allowing a stub being prefixed to the beginning of the ZIP file.

We could lean on this leniency to teach `ZipFile` how to read from a specified 
sub-range within the ZIP file. Instead of reading from the full length of the file, 
a new constructor could allow parsing ZIP files from the payload region of an entry 
using the STORED compression methods. This can be done without significant changes 
to the existing parsing logic.  

Similarly, a new constructor would allow `JarFile` to read JAR files packaged within 
a larger ZIP file.

### The jar:  URL scheme
The `jar` URL scheme implemented in `java.net.JarURLConnection` and 
`sun.net.www.protocol.jar` currently supports URLs such as 
`jar:file:app.jar!/HelloWorld.class`, which allows locating resources packaged 
within JAR files.

By supporting one more level of nesting, we can allow URLs such as 
`jar:file:app.jar!/library.jar!/HelloWorld.class`, where the resource
is located within a JAR file packaged within a larger ZIP or JAR file.

With the changes in `ZipFile` and `JarFile` described above, this can be implemented 
without needing to unpack the nested JAR file or otherwise causing a performance
degredation.

### URLClassLoader

The JAR file specification already allows a JAR file to request sibling JAR files to
be appended to the class path using the `Class-Path` manifest attribute.

We can update the specification of the `Class-Path` attribute to allow the referral of 
JAR files nested within the JAR file itself. Alternatively, we can add another attribute. 

### ModuleFinder

`java.lang.module.ModuleFinder` can be updated to leverage the support for nested JAR 
files in `JarFile` and the `jar` protocol level described above. This requires a new 
JAR file manifest attribute `Module-Path` which can list nested modules.

Consider the following example, where the application module `m2` requires a library
module `m1` and where `m1.jar` is packaged as an entry within `m2.jar`:

```
% jar tf m2.jar                                                                            
META-INF/MANIFEST.MF
module-info.class
m2/Main.class
META-INF/modules/m1.jar

% unzip -c  m2.jar META-INF/MANIFEST.MF                                                
Manifest-Version: 1.0
Main-Class: m2.Main
Module-Path: META-INF/modules/m1.jar
```

This packaging allows `ModuleFinder` to locate the `m1` module within the `m2` jar:

```
% java -p m2.jar --list-modules 
java.base@24-internal
[...omitted JDK modules]
m1 jar:/Users/eirbjo/Projects/jdk/m2.jar!/META-INF/modules/m1.jar
m2 file:///Users/eirbjo/Projects/jdk/m2.jar
```

Such an application can be launched using:

```
% java -p m2.jar -m m2
Hello from m1
```

### jlink

Some minor updates are needed in `jlink` to represent module archives using
`URI` instead of `Path`.

With these changes in place, we can use `jlink` to create a runtime image for
the single-jar `m2` application described above using the following command:

```
% jlink -p m2.jar --add-modules m2 --output image --launcher=m2=m2
image/bin/java --list-modules
java.base@24-internal
m1
m2
```

The application can be launched like this:
```
% image/bin/m2                 
Hello from m1
```

## Alternatives

Requests have been made to make the JAR signature implementation and ModuleFinder 
implementation more easily reusable outside the JDK. The work needed to define such 
public APIs would be significant, and the number of users of such APIs small. It 
seemds simpler to offer support for single-jars in the JDK itself. 

## Dependencies

The work done here needs to support mult-release JARs introduced in JEP 238 and 
modular JARs as defined in JEP 261
