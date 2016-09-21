# Building j2objc_protoc.exe for Windows #

For now, the project assumes that https://github.com/google/protobuf is checked out in a sibling directory
as follows:

```
- Root
-- protobuf-2.6.1
-- j2objc-1.1 (exact name doesn't matter)
```

So first, clone that repository and check out the necessary branch:

```
$ git clone https://github.com/google/protobuf protobuf-2.6.1
$ cd protobuf-2.6.1
$ git checkout v2.6.1
```

Then build `libprotobuf.lib` and `libprotoc.lib` as specified in the
[documentation](https://github.com/google/protobuf/blob/v2.6.1/vsprojects/readme.txt).

Once that's done, building `j2objc_protoc.exe` is as easy as opening the solution in
`protobuf/compiler/vsprojects` and building the project.
