package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.azure.management.resources.fluentcore.model.HasInner
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

class Table(
    private val inner: TableInner
) : HasInner<TableInner> {

    private val myRows = mutableListOf<TableRow>()
    private val myColumns = mutableListOf<TableColumn>()

    public val rows: List<TableRow> = myRows

    public val columns: List<TableColumn> = myColumns

    private val columnIndexes : Map<String, Int>

    init {
        val mutableColumnIndexes = mutableMapOf<String, Int>()
        columnIndexes = mutableColumnIndexes
        inner.columns.forEachIndexed { index, column ->
            myColumns.add(TableColumn(column))
            mutableColumnIndexes.put(column.name, index)
        }

        inner.rows.forEach {
            myRows.add(TableRow(it, this))
        }
    }
    override fun inner(): TableInner = inner

    fun getColumnIndex(columnName: String): Int? = columnIndexes.get(columnName)
}

class TableColumn(
    private val inner: TableColumnInner
) : HasInner<TableColumnInner> {
    override fun inner(): TableColumnInner = inner

}

class TableRow(
    private val inner: List<Any?>,
    private val owner: Table
) : HasInner<List<Any?>> {

    fun getStringValue(columnName: String, isRequired: Boolean = false, treatEmptyAsNull: Boolean = true) : String? {
        val colIndex = owner.getColumnIndex(columnName) ?: throw Exception("Could not read ResourceGrid response table. Incorrect columnName: ${columnName}")
        val value = inner[colIndex] ?: if (isRequired) throw Exception("Could not read ResourceGrid response table. Column ${columnName} contains null value") else null
        val column = owner.columns[colIndex]
        if (column.inner().type == "string") {
            val strValue = value as String?
            if (treatEmptyAsNull && strValue.isNullOrEmpty()) {
                return if (isRequired) throw Exception("Could not read ResourceGrid response table. Column ${columnName} contains empty value") else null
            }
            return strValue;
        }
        return value?.toString()
    }

    fun getDateTimeValue(columnName: String, isRequired: Boolean = false, treatEmptyAsNull: Boolean = true): DateTime? {
        val stringValue = getStringValue(columnName, isRequired, treatEmptyAsNull)
        return if (stringValue == null) null else ISODateTimeFormat.dateTimeParser().parseDateTime(stringValue)
    }

    fun getMapValue(columnName: String, isRequired: Boolean = false, treatEmptyAsNull: Boolean = true): Map<String, String>? {
        val stringValue = getStringValue(columnName, isRequired, treatEmptyAsNull)
        return if (stringValue == null) null else objectMapper.readValue(stringValue, STRING_MAP_TYPEREFERENCE)
    }
    override fun inner(): List<Any?> = inner

    companion object {
        private val objectMapper = ObjectMapper()
        private val STRING_MAP_TYPEREFERENCE: TypeReference<HashMap<String, String>> = object : TypeReference<HashMap<String, String>>() {}
    }
}
