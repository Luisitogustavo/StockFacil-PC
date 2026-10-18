namespace StockFacil.Api.Models;

public sealed class StockMovement
{
    public int Id { get; set; }
    public int ProductId { get; set; }
    public string Type { get; set; } = string.Empty;
    public int Quantity { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public string Notes { get; set; } = string.Empty;
    public Product? Product { get; set; }
}
