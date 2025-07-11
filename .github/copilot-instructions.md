# CoPilot Coding Instructions for Java Projects

## ✨ General Rules

- Write Java code compatible with **Java 17** as the minimum version.
- Prefer Java 17 language features (e.g. records, pattern matching).
- Avoid writing deeply nested logic or very long methods.
- Keep classes and methods short and single-purpose.
- Always write code that is thread-safe where applicable.
- Avoid “magic numbers.” Define constants with meaningful names.
- Avoid static utility classes unless absolutely necessary.

---

## ✅ Documentation

- Add **Javadoc** to all public classes, interfaces, methods, and fields.
- Document the purpose, parameters, return values, exceptions thrown, and usage examples where helpful.

---

## 📦 Project Structure

- Use **Maven** as the build tool.
- Group tests into these folders:
    - `src/test/java/unit`
    - `src/test/java/integration`
    - `src/test/java/bdd`
    - `src/test/java/regression`
- Keep test classes organized in packages matching the production code structure.

---

## 🔒 Security and Code Quality

Follow these security and quality rules inspired by SonarQube:

- **Avoid NullPointerException risks:**
    - Always check for null if it’s possible a value is null.
    - Prefer `Optional` where appropriate.
- **Close resources properly**:
    - Use try-with-resources for streams, readers, JDBC, etc.
- **Avoid hard-coded credentials or secrets.**
- **Avoid using deprecated Java APIs.**
- **Do not log sensitive data** like passwords or tokens.
- **Avoid empty catch blocks** unless explicitly justified.
- **Never catch `Throwable` unless absolutely required.**
- **Avoid using `System.out` or `System.err`.**
    - Use **SLF4J** for logging.
- **Never swallow exceptions silently.**
    - Always log exceptions or rethrow them.
- **Avoid commented-out code** in committed code.
- **Do not duplicate code unnecessarily.** Refactor repeated code into methods.
- **Avoid excessive method complexity.** Keep cyclomatic complexity low.
- **Use proper visibility modifiers**:
    - Minimize public and package-private fields.
    - Prefer private fields and methods unless wider access is needed.
- **Use generic types with collections** rather than raw types.
- **Avoid synchronized collections in new code.** Prefer concurrent collections.
- **Never ignore the result of method calls** unless justified.
- **Avoid writing classes with too many fields** (e.g. God classes).
- **Ensure proper equals and hashCode implementations** for classes used in collections.
- **Avoid throwing generic exceptions.** Use specific exception types.
- **Validate all input from external sources.**
- **Avoid using the default encoding.** Specify encoding explicitly for I/O.
- **Avoid using `finalize()`.** Use try-with-resources or cleaners.
- **Avoid excessive inheritance depth.** Prefer composition over inheritance.

---

## ⚙️ Logging

- Use **SLF4J** for logging.
- Do not use `System.out.println`.
- Always log exceptions with stack traces for troubleshooting.
- Do not log sensitive data.

---

## 🧪 Testing Rules

- **Unit tests**
    - Use **JUnit 5** (Jupiter).
    - Ensure high test coverage.
    - Use assertions to verify expected behavior.
- **Integration tests**
    - Write integration tests in `integration` folder.
    - Use **TestContainers** for required external services (e.g. databases, message queues).
- **BDD tests**
    - Write BDD tests using **Cucumber** in the `bdd` folder.
    - Define clear Given/When/Then steps.
- **Regression tests**
    - Maintain a dedicated `regression` folder.
- Write tests for:
    - Positive scenarios
    - Negative scenarios
    - Boundary conditions
- Always clean up test resources.
- Avoid test dependencies on specific machine or environment.
- Avoid sleeps in tests; use waits or polling mechanisms if needed.

---

## 🎨 Code Style

- Follow Google Java Style Guide or your organization’s preferred style.
- Use meaningful, descriptive names for variables, methods, and classes.
- Break long statements into multiple lines for readability.
- Use braces `{}` even for single-line `if` or loops.
- Organize imports:
    - No unused imports.
    - Static imports grouped separately.
- Keep lines under 120 characters where possible.
- Separate logical sections in code with blank lines.

---

## 💡 Performance

- Avoid premature optimization.
- Measure performance impacts for critical changes.
- Be mindful of object allocations in hot paths.
- Use streams responsibly (avoid nested or excessive streaming if not necessary).
- Use caching where applicable, but avoid overengineering.

---

## 🔗 Additional Sonar-like Best Practices

- Avoid using `instanceof` unnecessarily; prefer polymorphism where applicable.
- Avoid using floating-point numbers where exact precision is required (e.g. money).
- Handle InterruptedException properly; do not swallow it.
- Avoid BigInteger / BigDecimal unless necessary for precise calculations.
- Avoid public static non-final fields.
- Always implement serialVersionUID for Serializable classes.
- Avoid duplicate switch cases or if-else branches.
- Avoid classes that have only static members unless they represent constants.
- Avoid unused local variables or parameters.
- Avoid method parameters that exceed 7 parameters; consider objects to group them.
- Avoid synchronized methods unless required.
- Avoid excessive logging in loops.
- Avoid mixing different logging levels in the same code block unnecessarily.
- Avoid string concatenation in logs; use SLF4J placeholders.

---

# ✅ How to Prompt CoPilot

> When writing prompts to CoPilot or comments in code, use clear, specific instructions like:
>
> ```java
> // CoPilot: Write a method to fetch users by email from PostgreSQL using Spring Data JPA.
> // Add Javadoc. Use Java 17 features. Write unit tests with JUnit 5.
> ```
>
> or:
>
> ```java
> // CoPilot: Refactor this class to reduce cyclomatic complexity.
> // Keep all business logic. Add missing logging via SLF4J.
> ```

---

_Always code as if your teammates are the next maintainers!_

