package com.improving.tddair;

import org.junit.jupiter.api.Test;

class FlightTest {

    @Test
    void testGetFullFlightNumber() {
        // setup
        Flight flight = new Flight("DFW", "ORD", 924, "AA", 242);
        //assert
        assert flight.getFullFlightNumber().equals("AA242");
    }

    @Test
    void testGetFullFlightNumberNullAirline() {
        // setup
        Flight flight = new Flight("DFW", "ORD", 924, null, 242);
        //assert
        assert flight.getFullFlightNumber().equals("UNKNOWN");
    }
}