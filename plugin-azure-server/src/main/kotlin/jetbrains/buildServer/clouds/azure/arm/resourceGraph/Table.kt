package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.microsoft.azure.management.resources.fluentcore.model.HasInner

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
            val strValue = value as String
            if (treatEmptyAsNull && strValue.isEmpty()) {
                return if (isRequired) throw Exception("Could not read ResourceGrid response table. Column ${columnName} contains empty value") else null
            }
            return strValue;
        }
        return value?.toString()
    }
    override fun inner(): List<Any?> = inner

}
