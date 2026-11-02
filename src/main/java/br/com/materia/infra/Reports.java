package br.com.materia.infra;

import br.com.materia.domain.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Reports {
    private Reports(){}
    public static String date(String source){return LocalDateTime.parse(source).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));}
    public static List<String> materialHeaders(){return List.of("Código","Material","Categoria","Unidade","Localização","Fornecedor","Código de barras","Saldo","Mínimo","Custo médio (R$)","Preço (R$)","Valor em estoque (R$)","Situação","Observações");}
    public static List<List<String>> materialRows(List<Material> list){
        return list.stream().map(m->List.of(m.sku(),m.name(),m.category(),m.unit(),m.location(),m.supplierName(),m.barcode(),Numbers.qty(m.stock()),Numbers.qty(m.minimum()),Numbers.decimalMoney(m.costCents()),Numbers.decimalMoney(m.priceCents()),Numbers.valuation(m.stock(),m.costCents()).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString().replace('.',','),m.status(),m.notes())).toList();
    }
    public static List<String> movementHeaders(){return List.of("ID","Data/hora","Código","Material","Tipo","Quantidade","Unidade","Saldo após","Custo unitário (R$)","Motivo","Documento","Responsável");}
    public static List<List<String>> movementRows(List<Movement> list){
        return list.stream().map(m->List.of(Long.toString(m.id()),date(m.createdAt()),m.sku(),m.name(),m.typeLabel(),Numbers.qty(m.delta()),m.unit(),Numbers.qty(m.balance()),Numbers.decimalMoney(m.costCents()),m.reason(),m.reference(),m.operator())).toList();
    }
    public static void html(Path file,String company,List<Material> materials,List<Movement> movements,boolean history) throws Exception {
        List<Material> active=materials.stream().filter(Material::active).toList();
        BigDecimal total=active.stream().map(m->Numbers.valuation(m.stock(),m.costCents())).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<String> headers=history?movementHeaders():materialHeaders();
        List<List<String>> rows=history?movementRows(movements):materialRows(materials);
        StringBuilder out=new StringBuilder("""
            <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Materia • Relatório</title><style>
            body{font:14px system-ui,sans-serif;color:#213547;margin:40px}h1{font-size:28px;margin-bottom:6px}p{color:#526477}
            .brand{color:#087f75;font-weight:800;letter-spacing:2px}.summary{padding:20px;background:#edf6f3;border-radius:12px;margin:24px 0}
            table{border-collapse:collapse;width:100%;font-size:12px}th{text-align:left;background:#183a43;color:white}td,th{padding:10px;border-bottom:1px solid #dce4e8;vertical-align:top}tr:nth-child(even){background:#f5f8fa}
            @media print{body{margin:0}th{color:#183a43;background:#eee}thead{display:table-header-group}tr{break-inside:avoid}@page{size:A4 landscape;margin:12mm}}
            </style><div class="brand">MATERIA / GESTÃO DE MATERIAIS</div>
            """);
        out.append("<h1>").append(history?"Histórico de movimentações":"Inventário de materiais").append("</h1><p>").append(escape(company)).append(" · Emitido em ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("</p>");
        out.append("<div class='summary'>").append(rows.size()).append(" registros neste relatório");
        if(!history)out.append(" · Valor dos materiais ativos incluídos: <strong>").append(escape(Numbers.currency(total))).append("</strong>");
        out.append("</div><table><thead><tr>");
        for(String h:headers)out.append("<th>").append(escape(h)).append("</th>");
        out.append("</tr></thead><tbody>");
        for(List<String> row:rows){out.append("<tr>");for(String cell:row)out.append("<td>").append(escape(cell)).append("</td>");out.append("</tr>");}
        out.append("</tbody></table><p>Os saldos do inventário são atuais. O histórico registra o saldo após cada operação. Custos exibidos em reais; quantidades respeitam a unidade de cada material.</p></html>");
        Files.writeString(file,out,StandardCharsets.UTF_8);
    }
    public static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
