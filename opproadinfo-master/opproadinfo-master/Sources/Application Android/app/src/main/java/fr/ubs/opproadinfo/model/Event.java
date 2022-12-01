package fr.ubs.opproadinfo.model;

/**
 * Represents an event
 */
public class Event {
    private final double longitude;
    private final double latitude;
    private final String type;
    private final double endTime;
    private int id;

    /**
     * Instantiates a new Event.
     *
     * @param latitude  the latitude
     * @param longitude the longitude
     * @param type      the type
     * @param endTime   the end time
     * @param id        the id
     */
    public Event(double latitude, double longitude, String type, double endTime, int id) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.type = type;
        this.endTime = endTime;
        this.id = id;
    }


    /**
     * Gets longitude.
     *
     * @return the longitude
     */
    public double getLongitude() {
        return longitude;
    }


    /**
     * Gets latitude.
     *
     * @return the latitude
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Gets type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Gets end time.
     *
     * @return the end time
     */
    public double getEndTime() {
        return endTime;
    }

    /**
     * Gets id.
     *
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * Sets id.
     *
     * @param id the id
     */
    public void setId(int id) {
        this.id = id;
    }
}
