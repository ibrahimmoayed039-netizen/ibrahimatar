package com.fabric.ledger.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val fabrics: List<Fabric>,
    val customers: List<Customer>,
    val sales: List<Sale>,
    val items: List<SaleItem>,
    val payments: List<Payment>,
    val purchases: List<Purchase> = emptyList(),
    val supplierPayments: List<SupplierPayment> = emptyList(),
    val suppliers: List<Supplier> = emptyList()
)

/** نسخة احتياطية بصيغة JSON مقروءة، لا تعتمد على إصدار قاعدة البيانات */
object Backup {
    fun toJson(d: BackupData): String {
        val o = JSONObject()
        o.put("app", "FabricLedger")
        o.put("version", 2)
        o.put("fabrics", JSONArray().also { a ->
            d.fabrics.forEach { f ->
                a.put(JSONObject().put("id", f.id).put("name", f.name).put("type", f.type).put("color", f.color)
                    .put("unit", f.unit).put("retail", f.retailPrice).put("wholesale", f.wholesalePrice).put("stock", f.stock).put("minStock", f.minStock).put("rollLength", f.rollLength)
                    .put("retailUsd", f.retailPriceUsd).put("wholesaleUsd", f.wholesalePriceUsd)
                    .put("baleType", f.baleType).put("piecesPerBale", f.piecesPerBale)
                    .put("purchasePrice", f.purchasePrice).put("purchaseCurrency", f.purchaseCurrency)
                    .put("sealedBales", f.sealedBales).put("sealedMeters", f.sealedMeters).put("imagePath", f.imagePath))
            }
        })
        o.put("customers", JSONArray().also { a ->
            d.customers.forEach { c ->
                a.put(JSONObject().put("id", c.id).put("name", c.name).put("phone", c.phone).put("isWholesale", c.isWholesale))
            }
        })
        o.put("sales", JSONArray().also { a ->
            d.sales.forEach { s ->
                a.put(JSONObject().put("id", s.id).put("customerId", s.customerId).put("date", s.date)
                    .put("total", s.total).put("paid", s.paid).put("note", s.note).put("currency", s.currency))
            }
        })
        o.put("items", JSONArray().also { a ->
            d.items.forEach { i ->
                a.put(JSONObject().put("id", i.id).put("saleId", i.saleId).put("fabricId", i.fabricId).put("fabricName", i.fabricName)
                    .put("unit", i.unit).put("quantity", i.quantity).put("unitPrice", i.unitPrice).put("pieces", i.pieces))
            }
        })
        o.put("payments", JSONArray().also { a ->
            d.payments.forEach { p ->
                a.put(JSONObject().put("id", p.id).put("customerId", p.customerId).put("date", p.date)
                    .put("amount", p.amount).put("note", p.note).put("currency", p.currency))
            }
        })
        o.put("purchases", JSONArray().also { a ->
            d.purchases.forEach { p ->
                a.put(JSONObject().put("id", p.id).put("fabricId", p.fabricId).put("date", p.date)
                    .put("quantity", p.quantity).put("unitCost", p.unitCost).put("note", p.note)
                    .put("currency", p.currency).put("paid", p.paid).put("supplier", p.supplier).put("pieces", p.pieces)
                    .put("bales", p.bales).put("baleType", p.baleType))
            }
        })
        o.put("supplierPayments", JSONArray().also { a ->
            d.supplierPayments.forEach { s ->
                a.put(JSONObject().put("id", s.id).put("date", s.date).put("amount", s.amount)
                    .put("currency", s.currency).put("supplier", s.supplier).put("note", s.note))
            }
        })
        o.put("suppliers", JSONArray().also { a ->
            d.suppliers.forEach { x -> a.put(JSONObject().put("id", x.id).put("name", x.name).put("phone", x.phone)) }
        })
        return o.toString(2)
    }

    fun fromJson(text: String): BackupData {
        val o = JSONObject(text)
        require(o.optString("app") == "FabricLedger") { "ملف غير صالح" }
        fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
        return BackupData(
            fabrics = o.getJSONArray("fabrics").objs().map {
                Fabric(it.getLong("id"), it.getString("name"), it.getString("type"), it.getString("color"),
                    it.getString("unit"), it.getDouble("retail"), it.getDouble("wholesale"), it.getDouble("stock"), it.optDouble("minStock", 0.0), it.optDouble("rollLength", 0.0),
                    it.optDouble("retailUsd", 0.0), it.optDouble("wholesaleUsd", 0.0),
                    it.optString("baleType", ""), it.optDouble("piecesPerBale", 0.0),
                    it.optDouble("purchasePrice", 0.0), it.optString("purchaseCurrency", "IQD"),
                    it.optDouble("sealedBales", 0.0), it.optDouble("sealedMeters", 0.0), it.optString("imagePath", ""))
            },
            customers = o.getJSONArray("customers").objs().map {
                Customer(it.getLong("id"), it.getString("name"), it.getString("phone"), it.getBoolean("isWholesale"))
            },
            sales = o.getJSONArray("sales").objs().map {
                Sale(it.getLong("id"), it.getLong("customerId"), it.getLong("date"), it.getDouble("total"), it.getDouble("paid"), it.optString("note", ""), it.optString("currency", "IQD"))
            },
            items = o.getJSONArray("items").objs().map {
                SaleItem(it.getLong("id"), it.getLong("saleId"), it.getLong("fabricId"), it.getString("fabricName"),
                    it.getString("unit"), it.getDouble("quantity"), it.getDouble("unitPrice"), it.optDouble("pieces", 0.0))
            },
            payments = o.getJSONArray("payments").objs().map {
                Payment(it.getLong("id"), it.getLong("customerId"), it.getLong("date"), it.getDouble("amount"), it.getString("note"), it.optString("currency", "IQD"))
            },
            // النسخ القديمة بلا مشتريات تُقبل
            purchases = (o.optJSONArray("purchases")?.objs() ?: emptyList()).map {
                Purchase(it.getLong("id"), it.getLong("fabricId"), it.getLong("date"), it.getDouble("quantity"),
                    it.optDouble("unitCost", 0.0), it.optString("note", ""),
                    it.optString("currency", "IQD"), it.optDouble("paid", 0.0), it.optString("supplier", ""), it.optDouble("pieces", 0.0),
                    it.optDouble("bales", 0.0), it.optString("baleType", ""))
            },
            supplierPayments = (o.optJSONArray("supplierPayments")?.objs() ?: emptyList()).map {
                SupplierPayment(it.getLong("id"), it.getLong("date"), it.getDouble("amount"),
                    it.optString("currency", "IQD"), it.optString("supplier", ""), it.optString("note", ""))
            },
            suppliers = (o.optJSONArray("suppliers")?.objs() ?: emptyList()).map {
                Supplier(it.getLong("id"), it.getString("name"), it.optString("phone", ""))
            }
        )
    }
}
