namespace StockFacil.Api.Models;

public sealed class Category
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public bool Active { get; set; } = true;
    public ICollection<Product> Products { get; set; } = new List<Product>();
}
