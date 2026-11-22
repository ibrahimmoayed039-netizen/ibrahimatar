package com.fabric.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Fabric::class, Customer::class, Sale::class, SaleItem::class, Payment::class, Purchase::class, SupplierPayment::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** 1 → 2: إضافة حد تنبيه المخزون لكل صنف */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fabrics ADD COLUMN minStock REAL NOT NULL DEFAULT 0")
            }
        }
        /** 2 → 3: جدول المشتريات (الكميات المشتراة لكل صنف) */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "fabricId INTEGER NOT NULL, date INTEGER NOT NULL, quantity REAL NOT NULL, " +
                        "unitCost REAL NOT NULL, note TEXT NOT NULL)"
                )
            }
        }
        /** 3 → 4: سعر وعملة ومسدَّد ومورّد لكل شراء + جدول دفعات المورّدين */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE purchases_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "fabricId INTEGER NOT NULL, date INTEGER NOT NULL, quantity REAL NOT NULL, " +
                        "unitCost REAL NOT NULL, note TEXT NOT NULL, currency TEXT NOT NULL, " +
                        "paid REAL NOT NULL, supplier TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO purchases_new (id, fabricId, date, quantity, unitCost, note, currency, paid, supplier) " +
                        "SELECT id, fabricId, date, quantity, unitCost, note, 'IQD', 0, '' FROM purchases"
                )
                db.execSQL("DROP TABLE purchases")
                db.execSQL("ALTER TABLE purchases_new RENAME TO purchases")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS supplier_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "date INTEGER NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, " +
                        "supplier TEXT NOT NULL, note TEXT NOT NULL)"
                )
            }
        }
        /** 4 → 5: الإدخال بالقطعة (طول القطعة للصنف + عدد القطع في المبيعات والمشتريات) */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fabrics ADD COLUMN rollLength REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sale_items ADD COLUMN pieces REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE purchases ADD COLUMN pieces REAL NOT NULL DEFAULT 0")
            }
        }
        /** 5 → 6: ملاحظة للفاتورة (لقوائم الزبائن المسجّلة بمبلغ فقط) */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }
        /** 6 → 7: عملة (دينار/دولار) للفواتير والدفعات + أسعار الدولار للأصناف */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales ADD COLUMN currency TEXT NOT NULL DEFAULT 'IQD'")
                db.execSQL("ALTER TABLE payments ADD COLUMN currency TEXT NOT NULL DEFAULT 'IQD'")
                db.execSQL("ALTER TABLE fabrics ADD COLUMN retailPriceUsd REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fabrics ADD COLUMN wholesalePriceUsd REAL NOT NULL DEFAULT 0")
            }
        }
        /** 7 → 8: البالات (نوع البالة، قطع البالة، سعر الشراء) للأصناف وعدد البالات في المشتريات */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fabrics ADD COLUMN baleType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE fabrics ADD COLUMN piecesPerBale REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fabrics ADD COLUMN purchasePrice REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fabrics ADD COLUMN purchaseCurrency TEXT NOT NULL DEFAULT 'IQD'")
                db.execSQL("ALTER TABLE purchases ADD COLUMN bales REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE purchases ADD COLUMN baleType TEXT NOT NULL DEFAULT ''")
            }
        }
        /** 8 → 9: البالات المغلقة (لدعم «فتح بالة») + مسار صورة الصنف */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fabrics ADD COLUMN sealedBales REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fabrics ADD COLUMN sealedMeters REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fabrics ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
            }
        }
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "fabric_ledger.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { instance = it }
        }
    }
}
