package dev.xenos.economylogger.model;

/** An item stack that entered (delta > 0) or left (delta < 0) an inventory around a transaction. */
public record TxItem(int delta, byte[] data, String label) {
}
