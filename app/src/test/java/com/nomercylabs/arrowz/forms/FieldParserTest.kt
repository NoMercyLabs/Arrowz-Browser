/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.forms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page is the one input here that cannot be trusted to be well formed, so
 * every one of these asserts a refusal or an exact reading rather than that
 * parsing merely ran.
 */
class FieldParserTest {

    @Test
    fun aTextFieldIsReadWholeIncludingWhatThePageAlreadyRejected() {
        val field: FormField? = FieldParser.parse(
            """
            {"id":"fld1","kind":"text","label":"Email address","value":"someone@",
             "inputType":"email","required":true,"invalid":true,"maxLength":80,
             "multiline":false,"options":[]}
            """.trimIndent(),
        )

        assertEquals("fld1", field?.id)
        assertEquals(FieldKind.Text, field?.kind)
        assertEquals("Email address", field?.label)
        assertEquals("someone@", field?.value)
        assertEquals(FieldKeyboard.Email, field?.keyboard)
        assertTrue(field?.isRequired == true)
        assertTrue(field?.isInvalid == true)
        assertEquals(80, field?.maxLength)
    }

    @Test
    fun aSelectCarriesItsOptionsAndWhichOneIsChosen() {
        val field: FormField? = FieldParser.parse(
            """
            {"id":"fld2","kind":"select","label":"Country","value":"nl","inputType":"",
             "options":[{"label":"Belgium","value":"be","selected":false},
                        {"label":"Netherlands","value":"nl","selected":true}]}
            """.trimIndent(),
        )

        assertEquals(FieldKind.Select, field?.kind)
        assertEquals(2, field?.options?.size)
        assertEquals("Netherlands", field?.options?.get(1)?.label)
        assertEquals(1, field?.selectedIndex)
    }

    /** Opening a long country list at the top costs a hundred presses to get
     *  back to where the page already was, so the sheet needs this answer. */
    @Test
    fun aSelectWithNothingChosenReportsNoSelection() {
        val field: FormField? = FieldParser.parse(
            """
            {"id":"fld3","kind":"select","label":"","value":"","inputType":"",
             "options":[{"label":"One","value":"1","selected":false}]}
            """.trimIndent(),
        )
        assertEquals(-1, field?.selectedIndex)
    }

    @Test
    fun anUnlabelledFieldIsStillEditable() {
        val field: FormField? = FieldParser.parse(
            """{"id":"fld4","kind":"text","label":"","value":"","inputType":"text"}""",
        )
        assertEquals("", field?.label)
        assertEquals(FieldKind.Text, field?.kind)
    }

    // A failure in the middle of a focus has to end as "no field", leaving the
    // page's own editing in place, rather than taking the browser down.
    @Test
    fun malformedInputIsRefusedRatherThanThrown() {
        assertNull(FieldParser.parse(null))
        assertNull(FieldParser.parse(""))
        assertNull(FieldParser.parse("not json at all"))
        assertNull(FieldParser.parse("""{"kind":"text"}"""))
        assertNull(FieldParser.parse("""{"id":"fld5","kind":"checkbox"}"""))
    }

    @Test
    fun everyInputTypeThePageCanSendMapsToAKeyboard() {
        assertEquals(FieldKeyboard.Email, FieldParser.keyboardFor("email"))
        assertEquals(FieldKeyboard.Phone, FieldParser.keyboardFor("tel"))
        assertEquals(FieldKeyboard.Url, FieldParser.keyboardFor("url"))
        assertEquals(FieldKeyboard.Password, FieldParser.keyboardFor("password"))
        assertEquals(FieldKeyboard.Decimal, FieldParser.keyboardFor("number"))
        assertEquals(FieldKeyboard.Number, FieldParser.keyboardFor("date"))
        // Case is the page's business, not ours.
        assertEquals(FieldKeyboard.Email, FieldParser.keyboardFor("EMAIL"))
    }

    /** A keyboard offering too much is usable; one offering only digits for a
     *  field wanting a name is not. */
    @Test
    fun anUnknownInputTypeFallsBackToTextRatherThanToSomethingNarrower() {
        assertEquals(FieldKeyboard.Text, FieldParser.keyboardFor(""))
        assertEquals(FieldKeyboard.Text, FieldParser.keyboardFor("colour-picker-2026"))
    }
}
