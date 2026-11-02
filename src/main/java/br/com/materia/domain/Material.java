package br.com.materia.domain;

public record Material(long id, String sku, String name, String category, String unit,
                       String location, Long supplierId, String supplierName, String barcode,
                       long costCents, long priceCents, long minimum, long stock,
                       String notes, boolean active) {
    public String status() {
        if (!active) return "Arquivado";
        if (stock == 0) return "Sem estoque";
        if (stock <= minimum) return "Estoque baixo";
        return "Disponível";
    }
}
