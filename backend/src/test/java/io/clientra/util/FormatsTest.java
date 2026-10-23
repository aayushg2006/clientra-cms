package io.clientra.util;

import io.clientra.entity.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    @Test
    void inr_usesIndianGrouping() {
        assertThat(Formats.inr(new BigDecimal("482000"))).isEqualTo("₹4,82,000");
        assertThat(Formats.inr(new BigDecimal("62400.40"))).isEqualTo("₹62,400");
        assertThat(Formats.inr(new BigDecimal("950"))).isEqualTo("₹950");
        assertThat(Formats.inr(new BigDecimal("12345678"))).isEqualTo("₹1,23,45,678");
    }

    @Test
    void inrShort_usesLakhAndCrore() {
        assertThat(Formats.inrShort(new BigDecimal("1840000"))).isEqualTo("₹18.4L");
        assertThat(Formats.inrShort(new BigDecimal("482000"))).isEqualTo("₹4.8L");
        assertThat(Formats.inrShort(new BigDecimal("25000000"))).isEqualTo("₹2.5Cr");
    }

    @Test
    void phone_normalisesIndianNumbers() {
        assertThat(Formats.phone("+91 99300 12000")).isEqualTo("9930012000");
        assertThat(Formats.phone("099300-12000")).isEqualTo("9930012000");
        assertThat(Formats.phone("  ")).isNull();
    }

    @Test
    void label_titleCasesEnums() {
        assertThat(Formats.label(TicketStatus.IN_PROGRESS)).isEqualTo("In Progress");
        assertThat(Formats.label(io.clientra.entity.enums.Segment.SMB)).isEqualTo("SMB");
    }
}
