namespace StockFacil.Api.Models;

// Recibo permanente: repetir un envío devuelve el mismo producto sin volver a crearlo.
public sealed class ProductSubmission
{
    public string RequestId { get; set; } = string.Empty;
    public int ProductId { get; set; }
    public Product Product { get; set; } = null!;
}
