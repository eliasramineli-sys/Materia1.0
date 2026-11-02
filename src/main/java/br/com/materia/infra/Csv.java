package br.com.materia.infra;

import br.com.materia.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Csv {
    public static final List<String> HEADER=List.of("codigo","nome","categoria","unidade","localizacao","codigo_barras","custo","preco","estoque_minimo","estoque_inicial","observacoes");
    public record ImportBatch(List<Material> materials,List<Long> quantities) {}
    private Csv() {}
    public static void write(Path file,List<String> headers,List<List<String>> rows) throws IOException {
        try(BufferedWriter writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)) {
            writer.write('\ufeff'); line(writer,headers);
            for(List<String> row:rows) line(writer,row);
        }
    }
    private static void line(Writer w,List<String> row) throws IOException {
        for(int i=0;i<row.size();i++) {
            if(i>0) w.write(';');
            String v=row.get(i)==null?"":row.get(i);
            // Evita fórmulas ao abrir campos de texto exportados em planilhas.
            if(v.stripLeading().matches("(?s)^[=+@-].*") || v.startsWith("\t") || v.startsWith("\r")) v="'"+v;
            w.write('"'); w.write(v.replace("\"","\"\"")); w.write('"');
        }
        w.write("\r\n");
    }
    public static List<List<String>> parse(String source) {
        if(source.startsWith("\ufeff"))source=source.substring(1);
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>();
        StringBuilder field=new StringBuilder(); boolean quoted=false,closed=false;
        for(int i=0;i<source.length();i++) {
            char ch=source.charAt(i);
            if(quoted) {
                if(ch=='"') { if(i+1<source.length() && source.charAt(i+1)=='"') {field.append('"');i++;} else {quoted=false;closed=true;} }
                else field.append(ch);
            } else if(ch==';' || ch=='\r' || ch=='\n') {
                row.add(field.toString());field.setLength(0);closed=false;
                if(ch!=';') {
                    if(ch=='\r' && i+1<source.length() && source.charAt(i+1)=='\n')i++;
                    if(!(row.size()==1 && row.get(0).isBlank()))rows.add(List.copyOf(row));
                    row.clear();
                }
            } else if(ch=='"' && field.length()==0 && !closed) quoted=true;
            else {
                if(closed || ch=='"')throw new IllegalArgumentException("CSV com aspas inválidas.");
                field.append(ch);
            }
            if(field.length()>5000 || row.size()>30 || rows.size()>10001)throw new IllegalArgumentException("CSV excede o limite de 10.000 materiais ou tamanho de campo.");
        }
        if(quoted)throw new IllegalArgumentException("CSV contém aspas não fechadas.");
        if(field.length()>0 || !row.isEmpty() || closed) {row.add(field.toString());rows.add(List.copyOf(row));}
        return rows;
    }
    public static ImportBatch readImport(Path file) throws Exception {
        if(Files.size(file)>5_000_000)throw new IllegalArgumentException("O CSV deve ter no máximo 5 MB.");
        List<List<String>> rows=parse(Files.readString(file,StandardCharsets.UTF_8));
        if(rows.size()<2 || !rows.get(0).equals(HEADER))throw new IllegalArgumentException("Cabeçalho inválido. Use o modelo CSV disponível em Materiais.");
        List<Material> materials=new ArrayList<>();List<Long> quantities=new ArrayList<>();Set<String> codes=new HashSet<>();
        for(int i=1;i<rows.size();i++) {
            try {
                List<String> r=rows.get(i);
                if(r.size()!=HEADER.size())throw new IllegalArgumentException("São esperadas 11 colunas.");
                String code=r.get(0).strip().toUpperCase(Locale.ROOT);
                if(!codes.add(code))throw new IllegalArgumentException("Código repetido no arquivo: "+code);
                Material m=new Material(0,code,r.get(1),r.get(2),r.get(3).strip().toUpperCase(Locale.ROOT),r.get(4),null,"",r.get(5),Numbers.money(r.get(6)),Numbers.money(r.get(7)),Numbers.quantity(r.get(8)),0,r.get(10),true);
                long initial=Numbers.quantity(r.get(9));
                br.com.materia.service.InventoryService.validateMaterial(m,initial);
                materials.add(m);quantities.add(initial);
            } catch(IllegalArgumentException ex) {throw new IllegalArgumentException("Registro "+(i+1)+": "+ex.getMessage());}
        }
        return new ImportBatch(List.copyOf(materials),List.copyOf(quantities));
    }
    public static void template(Path file) throws IOException {
        write(file,HEADER,List.of(List.of("EX-001","Material de exemplo","Escritório","UN","Prateleira A","","12,50","20,00","5","10","Exemplo: altere ou remova esta linha antes de importar.")));
    }
}
