package br.com.materia.domain;

public record Movement(long id, long materialId, String sku, String name, String unit,
                       String type, long delta, long balance, long costCents,
                       String reason, String reference, String operator, String createdAt) {
    public String typeLabel() {
        return switch (type) { case "ENTRY" -> "Entrada"; case "EXIT" -> "Saída"; default -> "Ajuste"; };
    }
}
