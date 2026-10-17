package DatabaseService;

/** Retained entry point; IDs are now allocated atomically from the existing recorder tables. */
@Deprecated
class SetIds extends DatabaseInit {
    void setAllIds() {
        // No process-local counters: nextId() initializes each existing sequence lazily.
    }
}
