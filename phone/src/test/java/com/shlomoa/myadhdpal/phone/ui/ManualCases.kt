package com.shlomoa.myadhdpal.phone.ui

/**
 * Catalogue cases that no JVM unit test can run, listed here so `scripts/check-test-cases.sh` can see every id.
 * They are PASS only when `docs/test-reports/android-final.md` says so: CI cases by their CI job, manual cases when the
 * user reports them in `docs/work-log/android.md`. Nothing here is skipped or disabled; it is not a test.
 */
object ManualCases {
    /** Run by the user on the Galaxy A55 5G with the Galaxy Watch6 Classic. */
    val manual = listOf(
        "TC-A-009",
        "TC-A-030",
        "TC-A-038",
        "TC-A-044",
        "TC-A-052",
        "TC-A-060",
        "TC-A-061",
        "TC-A-071",
        "TC-A-072",
        "TC-A-088",
        "TC-A-103",
        "TC-A-109",
        "TC-A-116",
        "TC-A-139"
    )

    /** Checked by a Gradle task or CI job: `:phone:check`, `assembleRelease`, release-has-no-debug, pairing check. */
    val ci = listOf("TC-A-011", "TC-A-062", "TC-A-063", "TC-A-064", "TC-A-065", "TC-A-066", "TC-A-070")

    /** Documents reviewed by the user (7.4 to 7.6). */
    val docs = listOf("TC-A-067", "TC-A-068", "TC-A-069")
}
