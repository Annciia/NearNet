package com.nearnet.sessionlayer

import org.junit.Test


import org.junit.Assert.*


class MyLogicTest {

    @Test
    fun myPrintTest() {
        println("Działa!")
        val result = 2 + 2
        println("Wynik: $result")
        assert(result == 4)
    }
}