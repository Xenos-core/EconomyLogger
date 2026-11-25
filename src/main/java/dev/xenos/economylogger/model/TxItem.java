package dev.xenos.economylogger.model;

public record TxItem(int delta, byte[] data, String label) {
}
