package com.example.bemsaticket.ticket.dto;

import com.example.bemsaticket.ticket.model.Ticket;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TicketResponseDTO {
    private List<Ticket> tickets;
    private long totalTicketCount;
}
