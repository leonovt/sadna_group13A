package com.sadna.group13a.application.dto;

import java.util.List;

/**
 * Data Transfer Object for a VenueMap.
 */
public record VenueMapDTO(
    String id,
    String stagePosition,
    String entrances,
    List<ZoneDTO> zones
) {}
