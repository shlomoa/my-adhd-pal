# Android lane baseline (red)

Evidence that the tests for step 2.7 (dependency injection) fail before any implementation exists.
Red commit: 648ec9a (tests only, no implementation). Cases TC-A-011 (CI build) is covered by the green `:phone:check`.

## 2.7 Dependency injection

Command: `./gradlew :phone:testDebugUnitTest`. Exit 1, build fails before tests run.

```
e: [ksp] .../phone/di/DiGraphTest.kt:38: [Hilt] @UninstallModules should only include modules annotated with both
@Module and @InstallIn, but found: [ClockModule].
> Execution failed for task ':phone:kspDebugUnitTestKotlin'
BUILD FAILED
```

TC-A-010 and TC-A-012 are red: `ClockModule`, the `@HiltAndroidApp` application and the `@AndroidEntryPoint`
`MainActivity` do not exist yet.
