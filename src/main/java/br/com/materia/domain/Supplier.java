package br.com.materia.domain;

public record Supplier(long id, String name, String document, String contact, String phone,
                       String email, String address, String notes, boolean active) {
    @Override public String toString() { return name + (active ? "" : " (arquivado)"); }
}
