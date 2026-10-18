using System.Text.RegularExpressions;

namespace StockFacil.Api;

public static class InventoryRules
{
    public const int MaxUnits = 1_000_000;
    public const double MaxPrice = 9_999_999.99;

    public static string? Label(string? value, string field, int maxLength, bool required = true)
    {
        var text = value?.Trim() ?? "";
        if (text.Length == 0) return required ? $"{field}: completa este campo." : null;
        if (text.Length > maxLength) return $"{field}: admite hasta {maxLength} caracteres.";
        if (!text.Any(char.IsLetterOrDigit) ||
            !Regex.IsMatch(text, "^[\\p{L}\\p{M}\\p{N} .,_+()/&:'\"%°-]+$"))
            return $"{field}: utiliza letras, números y signos habituales como guion, punto o paréntesis.";
        return null;
    }

    public static string? Note(string? value, string field, int maxLength)
    {
        if (value is null) return null;
        if (value.Trim().Length > maxLength) return $"{field}: admite hasta {maxLength} caracteres.";
        if (value.Any(c => char.IsControl(c) && c != '\n' && c != '\r'))
            return $"{field}: contiene caracteres no admitidos.";
        return null;
    }

    public static string? Product(ProductInput input)
    {
        if (input.CategoryId <= 0) return "Selecciona una categoría activa.";
        var sku = input.Sku?.Trim() ?? "";
        if (!Regex.IsMatch(sku, "^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$"))
            return "SKU: usa de 1 a 40 letras o números, con guion, punto o guion bajo; sin espacios.";
        var error = Label(input.Name, "Nombre", 120)
            ?? Label(input.Brand, "Marca", 60, false)
            ?? Label(input.Model, "Modelo", 80, false);
        if (error is not null) return error;
        if (!double.IsFinite(input.Price) || input.Price < 0 || input.Price > MaxPrice ||
            Math.Abs(input.Price - Math.Round(input.Price, 2)) > 0.0000001)
            return "Precio: ingresa un valor entre 0 y 9 999 999,99 con hasta dos decimales.";
        if (input.Stock < 0 || input.Stock > MaxUnits || input.MinimumStock < 0 || input.MinimumStock > MaxUnits)
            return "Stock y mínimo: utiliza números enteros entre 0 y 1 000 000.";
        return null;
    }
}
