/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.j2objc.gen;

import com.google.devtools.j2objc.GenerationTest;
import com.google.devtools.j2objc.Options;

import java.io.IOException;

/**
 * Tests for {@link TypeDeclarationGenerator}.
 *
 * @author Keith Stanger
 */
public class TypeDeclarationGeneratorTest extends GenerationTest {

  public void testAnonymousClassDeclaration() throws IOException {
    String translation = translateSourceFile(
      "public class Example { Runnable run = new Runnable() { public void run() {} }; }",
      "Example", "Example.m");
    assertTranslation(translation, "@interface Example_$1 : NSObject < JavaLangRunnable >");
    assertTranslation(translation, "- (void)run;");
    // Outer reference is not required.
    assertNotInTranslation(translation, "Example *this");
    assertNotInTranslation(translation, "- (id)initWithExample:");
  }

  public void testAnonymousConcreteSubclassOfGenericAbstractType() throws IOException {
    String translation = translateSourceFile(
        "public class Test {"
        + "  interface FooInterface<T> { public void foo1(T t); public void foo2(); }"
        + "  abstract static class Foo<T> implements FooInterface<T> { public void foo2() { } }"
        + "  Foo<Integer> foo = new Foo<Integer>() {"
        + "    public void foo1(Integer i) { } }; }",
        "Test", "Test.m");
    assertTranslation(translation, "foo1WithId:(JavaLangInteger *)i");
  }

  public void testAccessorForStaticPrimitiveConstant() throws IOException {
    // Even though it's safe to access the define directly, we should add an
    // accessor to be consistent with other static variables.
    String translation = translateSourceFile(
        "class Test { static final int FOO = 1; }", "Test", "Test.h");
    assertTranslation(translation, "#define Test_FOO 1");
    assertTranslation(translation, "J2OBJC_STATIC_FIELD_GETTER(Test, FOO, jint)");
  }

  // Verify that accessor methods for static vars and constants are generated on request.
  public void testStaticFieldAccessorMethods() throws IOException {
    Options.setStaticAccessorMethods(true);
    String source = "class Test { "
        + "static String ID; "
        + "private static int i; "
        + "static final Test DEFAULT = new Test(); }";
    String translation = translateSourceFile(source, "Test", "Test.h");
    assertTranslation(translation, "+ (NSString *)ID;");
    assertTranslation(translation, "+ (void)setID:(NSString *)value;");
    assertTranslation(translation, "+ (Test *)DEFAULT;");
    assertNotInTranslation(translation, "+ (jint)i");
    assertNotInTranslation(translation, "+ (void)setI:(jint)value");
    assertNotInTranslation(translation, "+ (void)setDEFAULT:(Test *)value");
  }

  // Verify that accessor methods for static vars and constants aren't generated by default.
  public void testNoStaticFieldAccessorMethods() throws IOException {
    String source = "class Test { "
        + "static String ID; "
        + "private static int i; "
        + "static final Test DEFAULT = new Test(); }";
    String translation = translateSourceFile(source, "Test", "Test.h");
    assertNotInTranslation(translation, "+ (NSString *)ID");
    assertNotInTranslation(translation, "+ (void)setID:(NSString *)value");
    assertNotInTranslation(translation, "+ (Test *)DEFAULT");
    assertNotInTranslation(translation, "+ (jint)i");
    assertNotInTranslation(translation, "+ (void)setI:(jint)value");
    assertNotInTranslation(translation, "+ (void)setDEFAULT:(Test *)value");
  }

  // Verify that accessor methods for enum constants are generated on request.
  public void testEnumConstantAccessorMethods() throws IOException {
    Options.setStaticAccessorMethods(true);
    String source = "enum Test { ONE, TWO }";
    String translation = translateSourceFile(source, "Test", "Test.h");
    assertTranslation(translation, "+ (TestEnum *)ONE;");
    assertTranslation(translation, "+ (TestEnum *)TWO;");
  }

  // Verify that accessor methods for enum constants are not generated by default.
  public void testNoEnumConstantAccessorMethods() throws IOException {
    String source = "enum Test { ONE, TWO }";
    String translation = translateSourceFile(source, "Test", "Test.h");
    assertNotInTranslation(translation, "+ (TestEnum *)ONE");
    assertNotInTranslation(translation, "+ (TestEnum *)TWO");
  }

  public void testNoStaticFieldAccessorForPrivateInnerType() throws IOException {
    Options.setStaticAccessorMethods(true);
    String translation = translateSourceFile(
        "class Test { private static class Inner1 { "
        + "public static class Inner2 { static String ID; } } }", "Test", "Test.m");
    assertNotInTranslation(translation, "+ (NSString *)ID");
    assertNotInTranslation(translation, "+ (void)setID:");
  }

  public void testStaticFieldAccessorInInterfaceType() throws IOException {
    Options.setStaticAccessorMethods(true);
    String translation = translateSourceFile(
        "interface Test { public static final boolean FOO = true; }", "Test", "Test.h");
    // The static accessor must go in the companion class, not the @protocol.
    assertTranslatedLines(translation,
        "@interface Test : NSObject",
        "",
        "+ (jboolean)FOO;");
  }

  public void testProperties() throws IOException {
    String source =
        "import com.google.j2objc.annotations.Property; "
        + "public class FooBar {"
        + "  @Property(\"readonly, nonatomic\") private int fieldBar, fieldBaz;"
        + "  @Property(\"readwrite\") private String fieldCopy;"
        + "  @Property private boolean fieldBool;"
        + "  @Property(\"nonatomic, readonly, weak\") private int fieldReorder;"
        + "  public int getFieldBaz() { return 1; }"
        + "  public void setFieldNonAtomic(int value) { }"
        + "  public void setFieldBaz(int value, int option) { }"
        + "  public boolean isFieldBool() { return fieldBool; }"
        + "}";
    String translation = translateSourceFile(source, "FooBar", "FooBar.h");
    assertTranslation(translation, "@property (readonly, nonatomic) jint fieldBar;");

    // Should split out fieldBaz and include the declared getter.
    assertTranslation(translation,
        "@property (readonly, nonatomic, getter=getFieldBaz) jint fieldBaz;");

    // Set copy for strings and drop readwrite.
    assertTranslation(translation,
        "@property (copy) NSString *fieldCopy;");

    // Test boolean getter.
    assertTranslation(translation,
        "@property (nonatomic, getter=isFieldBool) jboolean fieldBool;");

    // Reorder property attributes and pass setter through.
    assertTranslation(translation,
        "@property (weak, readonly, nonatomic) jint fieldReorder;");
  }

  public void testSynchronizedPropertyGetter() throws IOException {
    String source = "import com.google.j2objc.annotations.Property; "
        + "public class FooBar {"
        + "  @Property(\"getter=getfieldBar\") private int fieldBar;"
        + "  public synchronized int getFieldBar() { return fieldBar; }"
        + "}";
    String translation = translateSourceFile(source, "FooBar", "FooBar.h");
    assertTranslation(translation, "@property (getter=getfieldBar) jint fieldBar;");
  }

  public void testBadPropertyAttribute() throws IOException {
    String source = "import com.google.j2objc.annotations.Property; "
        + "public class FooBar {"
        + "  @Property(\"cause_exception\") private int fieldBar;"
        + "}";
    translateSourceFile(source, "FooBar", "FooBar.h");
    assertErrorCount(1);
  }

  public void testBadPropertySetterSelector() throws IOException {
    String source = "import com.google.j2objc.annotations.Property; "
        + "public class FooBar {"
        + "  @Property(\"setter=needs_colon\") private int fieldBar;"
        + "}";
    translateSourceFile(source, "FooBar", "FooBar.h");
    assertErrorCount(1);
  }

  public void testNonexistentPropertySetter() throws IOException {
    String source = "import com.google.j2objc.annotations.Property; "
        + "public class FooBar {"
        + "  @Property(\"setter=nonexistent:\") private int fieldBar;"
        + "}";
    translateSourceFile(source, "FooBar", "FooBar.h");
    assertErrorCount(1);
  }

  public void testPropertyWeakAssignment() throws IOException {
    String source = "import com.google.j2objc.annotations.Property; "
        + "import com.google.j2objc.annotations.Weak; "
        + "public class Foo {"
        + "  @Property(\"weak\") Foo barA;"
        + "  @Property(\"readonly\") @Weak Foo barB;"
        + "  @Property(\"weak, readonly\") @Weak Foo barC;"
        + "}";
    String translation = translateSourceFile(source, "Foo", "Foo.h");
    // Add __weak instance variable
    assertTranslation(translation, "__weak Foo *barA_;");
    assertNotInTranslation(translation, "J2OBJC_FIELD_SETTER(Foo, barA_, Foo *)");
    // Add weak property attribute
    assertTranslation(translation, "@property (weak, readonly) Foo *barB;");
    assertNotInTranslation(translation, "J2OBJC_FIELD_SETTER(Foo, barB_, Foo *)");
    // Works with both
    assertTranslation(translation, "__weak Foo *barC_;");
    assertTranslation(translation, "@property (weak, readonly) Foo *barC;");
    assertNotInTranslation(translation, "J2OBJC_FIELD_SETTER(Foo, barC_, Foo *)");
  }

  public void testWeakPropertyWithStrongAttribute() throws IOException {
    String source = "import com.google.j2objc.annotations.Property; "
        + "import com.google.j2objc.annotations.Weak; "
        + "public class Foo {"
        + "  @Property(\"strong\") @Weak Foo barA;"
        + "}";
    translateSourceFile(source, "Foo", "Foo.h");
    assertErrorCount(1);
  }
}
