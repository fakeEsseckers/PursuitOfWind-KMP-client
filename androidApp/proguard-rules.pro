# R8 rules of the app itself. Libraries bring their own consumer rules; add a rule here only for something R8 cannot see.

# Stack traces in crash reports keep file names and line numbers; the mapping file restores the rest.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
