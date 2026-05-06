package com.sadna.group13a.application.DTO;

import java.util.List;

public class VenueMapDTO {
    private final String id;
    private final String venueName;
    private final List<ZoneDTO> zones;

    public VenueMapDTO(String id, String venueName, List<ZoneDTO> zones) {
        this.id = id;
        this.venueName = venueName;
        this.zones = zones;
    }

    public String getId() { return id; }
    public String getVenueName() { return venueName; }
    public List<ZoneDTO> getZones() { return zones; }
}
