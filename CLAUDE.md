# CLAUDE.md

Appium page-object test framework for Android and iOS. Java 11, Gradle, TestNG, Appium java-client 10.

## Commands

```bash
./gradlew -q compileJava                      # compile
./gradlew startTests -Ppom.testNGxml=suites/local.xml -Ppom.env=config/config.properties -Ppom.debug=true
./gradlew startTests -Ppom.testNGxml=suites/local-ios.xml -Ppom.env=config/config.properties
```

To run one test, edit the `<class>` / `<include>` of a suite file. Other properties are listed in README.md.

## Layout

- `src/core/automation` - `AbstractDriverWrapper` -> `DriverWrapper` (Android) / `IOSDriverWrapper` (iOS), `Drivers` (capabilities), `ADB`, `selfhealing/`
- `src/core/reports` - `Log` (HTML step log)
- `src/bl/mobile/common` - `BaseAbstractScreen` (implements `IVerifiable`), `BaseUIElement`
- `src/bl/mobile/android`, `src/bl/mobile/ios` - page objects; base classes in `common/`
- `src/tests/android` (extend `BaseTest`), `src/tests/ios` (extend `IOSBaseTest`)
- `config/config.properties` - app ids and Appium URL; `suites/` - TestNG suites

## Rules

- Java 11 only: no records, no switch expressions, no `var`. Don't change Java, Gradle or dependency versions, and don't add dependencies.
- All Appium calls go through the driver wrapper, never the raw driver.
- Page objects hold locators, actions and `IVerifiable` verification only. No test logic, and no platform if/else in tests.
- One screen = one page-object class. iOS classes are prefixed `IOS`.
- Wait only with the wrapper's explicit waits (`waitToAppear`, `waitToDisappear`, `findElement(..., timeout)`). No `Thread.sleep` in tests or page objects.
- Assert with `log.verify` (soft) or `log.assertTrue` (hard); end every test with `log.verifyTestStatus()`.
- Handle permission dialogs explicitly; every test starts from a fresh app state.
- Write the simplest code that works and reads top to bottom like the scenario. One action per line, descriptive names.
- Comments explain why, never what. No Javadoc on trivial methods or on tests.
- No dead code, commented-out code or unused imports. Delete code you replace.
- No new interface, base class, factory or helper unless three call sites need it today. No wrappers for one-liners.
- Catch exceptions only to recover or add context; never swallow them in test code.
- Limits for new code: method 40 lines, class 300 lines, nesting 3 levels, 5 parameters. The driver wrappers are larger than this; don't split them unless asked.
- No credentials in the repo. Secrets come from environment variables; the self-healing API key from `ANTHROPIC_API_KEY`.
- Run `./gradlew -q compileJava` before handing over a change.

## Skills

`.claude/skills/`: `create-android-page-object`, `create-ios-page-object`, `create-android-test`, `create-ios-test`, `review-test-code`.

Reviews follow `review-test-code`: one review with the `code-reviewer` agent (`.claude/agents/`), then a second `code-reviewer` on a clean context that answers confirm, refute or unsure for each finding.
