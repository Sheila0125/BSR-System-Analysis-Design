package com.bsr.customerrequest.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RentalRequestTest {

    @Test
    @DisplayName("AC-UC01/AC-BR04 submission keeps this request's skill and does not preallocate equipment")
    void submissionKeepsCurrentSkillAndOnlyCategoryRequirements() {
        List<RentalRequestItem> submittedItems = new ArrayList<>(List.of(
                new RentalRequestItem("SKI", 2, "One shorter pair"),
                new RentalRequestItem("BOOTS", 1, null)));

        RentalRequest request = request("ADVANCED", submittedItems);
        submittedItems.clear();

        assertEquals("ADVANCED", request.skillLevel());
        assertEquals(2, request.items().size());
        assertEquals("SKI", request.items().getFirst().equipmentCategory());
        assertEquals(RentalRequestStatus.PENDING, request.status());
        assertNull(request.acceptedByEmployeeId());
        assertThrows(UnsupportedOperationException.class, () -> request.items().clear());
    }

    @Test
    @DisplayName("AC-BR05 supplemented request returns to pending and can be accepted then converted")
    void controlledAcceptedFlow() {
        RentalRequest request = request("BEGINNER", items());

        request.requestMoreInformation();
        assertEquals(RentalRequestStatus.NEEDS_INFO, request.status());

        request.resubmitSupplementedInformation();
        request.accept(41L);
        assertEquals(RentalRequestStatus.ACCEPTED, request.status());
        assertEquals(41L, request.acceptedByEmployeeId());

        request.markConverted();
        assertEquals(RentalRequestStatus.CONVERTED, request.status());
    }

    @Test
    @DisplayName("AC-BR05 employee can mark a pending or accepted request unavailable")
    void unavailableFlow() {
        RentalRequest pending = request("INTERMEDIATE", items());
        pending.markUnavailable();
        assertEquals(RentalRequestStatus.UNAVAILABLE, pending.status());

        RentalRequest accepted = request("INTERMEDIATE", items());
        accepted.accept(42L);
        accepted.markUnavailable();
        assertEquals(RentalRequestStatus.UNAVAILABLE, accepted.status());
    }

    @Test
    @DisplayName("AC-BR05 illegal transitions are rejected without changing the current stage")
    void illegalTransitionIsRejected() {
        RentalRequest request = request("BEGINNER", items());

        assertThrows(IllegalStateException.class, request::markConverted);
        assertEquals(RentalRequestStatus.PENDING, request.status());

        assertThrows(IllegalArgumentException.class, () -> request.accept(0L));
        assertEquals(RentalRequestStatus.PENDING, request.status());
        assertNull(request.acceptedByEmployeeId());

        request.accept(43L);
        assertThrows(IllegalStateException.class, request::requestMoreInformation);
        assertEquals(RentalRequestStatus.ACCEPTED, request.status());
    }

    @Test
    @DisplayName("AC-UC01 invalid days, empty items, and non-positive quantities are rejected")
    void submissionCompletenessIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> RentalRequest.submit(
                "REQ-1001",
                11L,
                21L,
                LocalDate.of(2026, 12, 20),
                0,
                "BEGINNER",
                LocalDateTime.of(2026, 9, 30, 12, 0),
                items()));
        assertThrows(IllegalArgumentException.class, () -> RentalRequest.submit(
                "REQ-1001",
                11L,
                21L,
                LocalDate.of(2026, 12, 20),
                3,
                "BEGINNER",
                LocalDateTime.of(2026, 9, 30, 12, 0),
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RentalRequestItem("SKI", 0, null));
    }

    private RentalRequest request(String skillLevel, List<RentalRequestItem> items) {
        return RentalRequest.submit(
                "REQ-1001",
                11L,
                21L,
                LocalDate.of(2026, 12, 20),
                3,
                skillLevel,
                LocalDateTime.of(2026, 9, 30, 12, 0),
                items);
    }

    private List<RentalRequestItem> items() {
        return List.of(new RentalRequestItem("SKI", 1, null));
    }
}
