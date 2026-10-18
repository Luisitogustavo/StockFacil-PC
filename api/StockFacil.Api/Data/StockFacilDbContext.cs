using Microsoft.EntityFrameworkCore;
using StockFacil.Api.Models;

namespace StockFacil.Api.Data;

public sealed class StockFacilDbContext(DbContextOptions<StockFacilDbContext> options)
    : DbContext(options)
{
    public DbSet<Category> Categories => Set<Category>();
    public DbSet<Product> Products => Set<Product>();
    public DbSet<ProductSubmission> ProductSubmissions => Set<ProductSubmission>();
    public DbSet<StockMovement> StockMovements => Set<StockMovement>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<ProductSubmission>().HasKey(receipt => receipt.RequestId);
        modelBuilder.Entity<ProductSubmission>()
            .HasOne(receipt => receipt.Product).WithMany()
            .HasForeignKey(receipt => receipt.ProductId).OnDelete(DeleteBehavior.Restrict);
        modelBuilder.Entity<Category>()
            .HasIndex(category => category.Name)
            .IsUnique();

        modelBuilder.Entity<Product>()
            .HasIndex(product => product.Sku)
            .IsUnique();
        modelBuilder.Entity<Product>().Property(product => product.Stock).IsConcurrencyToken();
        modelBuilder.Entity<Product>().Property(product => product.Active).IsConcurrencyToken();

        modelBuilder.Entity<Category>()
            .HasMany(category => category.Products)
            .WithOne(product => product.Category)
            .HasForeignKey(product => product.CategoryId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<Product>()
            .HasMany(product => product.StockMovements)
            .WithOne(movement => movement.Product)
            .HasForeignKey(movement => movement.ProductId)
            .OnDelete(DeleteBehavior.Restrict);
    }
}
