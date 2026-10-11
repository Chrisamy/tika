The focal method is `${method_sig}` in the focal class `${class_name}`, and their information is
```${full_fm?replace(r"(?m)^[ \t]*private [^\n{]*;[ \t]*\r?\n", "", "r")}```.
<#if (file_content!"")?has_content>

The public API of the focal class `${class_name}` (use these methods to set up and to verify state):
```
<#list file_content?matches(r"^[ \t]*public [^;{=]*?\)", "m") as sig>
${sig?trim}
</#list>
```
</#if>

API reference of the project types used by `${class_name}` (these are the only methods and constructors that exist on them):
```
<#include "api/" + class_name + ".txt" parse=false ignore_missing=true>
```
<#if other_method_sigs?has_content>
    Signatures of Other methods in the focal class are `${other_method_sigs}`.
</#if>
<#list c_deps?keys as key>
    The brief information of dependent class `${key}` is
    ```${c_deps[key]}```.
</#list>
<#list m_deps?keys as key>
    The brief information of dependent class `${key}` is
    ```${m_deps[key]}```.
</#list>
