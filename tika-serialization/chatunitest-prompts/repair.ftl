I need you to fix an error in a unit test, an error occurred while compiling and executing

The unit test is:
```
${unit_test}
```

The error message is:
```
${error_message}
```

The unit test is testing the method `${method_sig}` in the class `${class_name}`,
the source code of the method under test and its class is:
```
${full_fm?replace(r"(?m)^[ \t]*private [^\n{]*;[ \t]*\r?\n", "", "r")}
```
<#if (file_content!"")?has_content>
The public API of the class `${class_name}` (use these methods to set up and to verify state):
```
<#list file_content?matches(r"^[ \t]*public [^;{=]*?\)", "m") as sig>
${sig?trim}
</#list>
```
<#assign static_blocks = file_content?matches(r"^[ \t]*static \{.*?^[ \t]*\}", "ms")>
<#if static_blocks?has_content>
Static initialization of `${class_name}`. It shows the fixed contents of its private collections; tests cannot change them, but can rely on these values when choosing inputs and expected results:
```
<#list static_blocks as block>
${block}
</#list>
```
</#if>
</#if>

API reference of the project types used by `${class_name}` (these are the only methods and constructors that exist on them):
```
<#include "api/" + class_name + ".txt" parse=false ignore_missing=true>
```
<#if other_method_sigs?has_content>
```
The signatures of other methods in its class are `${other_method_sigs}`
```
</#if>

Fix every error listed above and return the whole fixed unit test. Keep these rules:
- Test only the method `${method_sig}`; delete any test case written for a different method of the class instead of fixing it.
- JUnit 5 and Mockito 5 only; no reflection, no access to private fields, methods or constructors.
- Call static methods on the class directly and verify results through the public API listed above.
- Mock collaborators with Mockito.mock(...) (no @ExtendWith(MockitoExtension.class)); for methods returning Class<?> use doReturn(X.class).when(mock).method(args).
- If a "cannot find symbol" error names a class, add the missing import.
- If a "cannot find symbol" error names a method or constructor, it does not exist: delete that call and use only methods listed in the public API and API reference above.
- If an assertion fails, re-read the source code of the method under test and assert what it actually does.
- Never add to, remove from or clear the class's collections (also not through the sets returned by its getters, which are unmodifiable); pick inputs from the static initialization shown above instead.
Return only the Java code in a single ```java block, with no explanation.
