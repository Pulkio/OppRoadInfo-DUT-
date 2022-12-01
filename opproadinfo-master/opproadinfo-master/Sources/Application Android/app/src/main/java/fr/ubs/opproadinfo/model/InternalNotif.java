package fr.ubs.opproadinfo.model;

/**
 * Represents all notifications types that show up in the app.
 */
public class InternalNotif {
    private final Event event;
    private final boolean isConfirmation;

    /**
     * Instantiates a new Internal notif.
     *
     * @param event          the event
     * @param isConfirmation the is confirmation
     */
    public InternalNotif(Event event, boolean isConfirmation) {
        this.event = event;
        this.isConfirmation = isConfirmation;
    }

    /**
     * Gets event.
     *
     * @return the event
     */
    public Event getEvent() {
        return event;
    }

    /**
     * Is confirmation boolean.
     *
     * @return the boolean
     */
    public boolean isConfirmation() {
        return isConfirmation;
    }
}
