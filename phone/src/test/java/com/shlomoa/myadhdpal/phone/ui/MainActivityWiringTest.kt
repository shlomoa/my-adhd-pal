package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shlomoa.myadhdpal.phone.MainActivity
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.di.LinkModule
import com.shlomoa.myadhdpal.phone.link.DataLayerClient
import com.shlomoa.myadhdpal.phone.link.DataLayerWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.link.FakeDataLayerClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real Hilt graph (Room, DataStore, repositories) with only the Play services client replaced. */
@HiltAndroidTest
@UninstallModules(LinkModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MainActivityWiringTest {
    @Module
    @InstallIn(SingletonComponent::class)
    object FakeLinkModule {
        @Provides
        @Singleton
        fun client(): DataLayerClient = FakeDataLayerClient()

        @Provides
        @Singleton
        fun source(client: DataLayerClient, clock: Clock): DataLayerWatchLinkDataSource =
            DataLayerWatchLinkDataSource(client, clock)

        @Provides
        fun watchLink(source: DataLayerWatchLinkDataSource): WatchLinkDataSource = source
    }

    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Test
    fun `TC-A-087 MainActivity shows the link state and the history from the real graph`() {
        compose.onNodeWithText("Never synced").assertExists()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Nothing from your watch yet", substring = true).assertExists()
    }
}
