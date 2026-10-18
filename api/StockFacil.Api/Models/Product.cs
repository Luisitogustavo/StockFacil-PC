namespace StockFacil.Api.Models;

public sealed class Product
{
    public int Id { get; set; }
    public int CategoryId { get; set; }
    public string Sku { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Brand { get; set; } = string.Empty;
    public string Model { get; set; } = string.Empty;
    public double Price { get; set; }
    public int Stock { get; set; }
    public int MinimumStock { get; set; }
    public bool Active { get; set; } = true;
    public Category? Category { get; set; }
    public ICollection<StockMovement> StockMovements { get; set; } = new List<StockMovement>();
}
