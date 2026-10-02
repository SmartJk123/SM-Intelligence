package com.example.smartmoney

import com.example.smartmoney.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationScreenTest {

    @Test
    fun screen_initialization_does_not_throw_null_pointer() {
        assertNotNull(Screen.Dashboard.route)
        assertEquals("dashboard", Screen.Dashboard.route)
        assertEquals(11, Screen.allScreens.size)
        assertEquals(4, Screen.bottomBarScreens.size)
        assertTrue(Screen.bottomBarScreens.contains(Screen.Dashboard))
        assertTrue(Screen.bottomBarScreens.contains(Screen.Accounts))
        assertTrue(Screen.bottomBarScreens.contains(Screen.Transactions))
        assertTrue(Screen.bottomBarScreens.contains(Screen.Menu))
        assertEquals("menu", Screen.Menu.route)
        assertEquals("More", Screen.Menu.title)
    }
}
