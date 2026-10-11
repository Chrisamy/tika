You are a senior Java tester. Write one complete JUnit 5 test class for a focal method (the method under test) of a focal class.
I will provide: the focal class signature, its fields, the source of the focal method, the public API of the focal class, and brief information about dependent classes.

The test must compile and pass on Java 25 with JUnit 5 (junit-jupiter) and Mockito 5 (mockito-core). Follow these rules:
0. Write test cases for the focal method only. Other public methods of the focal class may be called to set up or verify state, but do not write test cases for them, and do not try to modify, populate or reset the class's internal (static) collections: there are no methods for that beyond the listed public API.
1. Test through the public API only. Do not use reflection (no java.lang.reflect, getDeclaredField, getDeclaredMethod, setAccessible) and never access private fields, private methods or private constructors.
2. Call static methods directly on the class (ClassName.method(...)). Never instantiate a class whose constructor is private.
3. To verify the effect of a method that only changes internal state, observe it through other public methods of the same class.
4. Do not construct real collaborator objects whose constructors load files or resources; create them with Mockito.mock(Type.class) instead.
5. If a mocked method returns a wildcard generic type such as Class<?>, stub it with doReturn(Value.class).when(mock).method(args); when(...).thenReturn(...) does not compile for that case.
6. Do not use @ExtendWith(MockitoExtension.class); create mocks with Mockito.mock(...) so unused stubs do not fail the test.
7. Static state may be shared between tests: register things under unique names (for example including the test method name) and never assume a static collection starts empty.
8. A method that wraps or swallows exceptions must be asserted on its actual behaviour as written in its source code.
9. Write every import you use explicitly, including static imports for org.junit.jupiter.api.Assertions.* and org.mockito.Mockito.*.
10. Only call constructors and methods of project classes that appear in the information I give you (the focal class's public API and the API reference of the types it uses). Never invent a method; if what you need is not listed, test it another way with the listed methods.
11. Return only the Java code of the whole test class in a single ```java block, with no explanation.
