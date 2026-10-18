using Microsoft.EntityFrameworkCore;
using StockFacil.Api.Data;
using StockFacil.Api.Models;
using StockFacil.Api;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("StockFacil")
    ?? "Data Source=stockfacil-api.db";

builder.Services.AddDbContext<StockFacilDbContext>(options =>
    options.UseSqlite(connectionString));
builder.Services.AddCors(options => options.AddDefaultPolicy(policy =>
    policy.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod()));

var app = builder.Build();
app.UseCors();
app.Use(async (context, next) =>
{
    try { await next(context); }
    catch (DbUpdateConcurrencyException) when (!context.Response.HasStarted)
    {
        context.Response.StatusCode = StatusCodes.Status409Conflict;
        await context.Response.WriteAsJsonAsync(new { message = "El registro cambió durante la operación. Actualiza los datos antes de volver a intentarlo." });
    }
    catch (DbUpdateException error) when (!context.Response.HasStarted &&
        error.InnerException is Microsoft.Data.Sqlite.SqliteException { SqliteErrorCode: 19 })
    {
        context.Response.StatusCode = StatusCodes.Status409Conflict;
        await context.Response.WriteAsJsonAsync(new { message = "El registro coincide con otro existente o tiene datos relacionados. Actualiza la lista y revisa los datos." });
    }
});
app.UseDefaultFiles();
app.UseStaticFiles();

await InitializeDatabaseAsync(app);

app.MapGet("/api/health", () => Results.Ok(new { status = "ok", app = "StockFácil PC" }));

var categories = app.MapGroup("/api/categories");
categories.MapGet("/", async (StockFacilDbContext db) =>
    Results.Ok(await db.Categories.AsNoTracking()
        .Where(category => category.Active)
        .OrderBy(category => category.Name)
        .Select(category => new { category.Id, category.Name, category.Description })
        .ToListAsync()));

categories.MapPost("/", async (CategoryInput input, StockFacilDbContext db) =>
{
    var error = InventoryRules.Label(input.Name, "Categoría", 80)
        ?? InventoryRules.Note(input.Description, "Descripción", 300);
    if (error is not null) return Results.BadRequest(new { message = error });
    var name = input.Name.Trim();
    if (name.Length == 0)
        return Results.BadRequest(new { message = "El nombre de la categoría es obligatorio." });

    if (await db.Categories.AnyAsync(category => category.Name.ToLower() == name.ToLower()))
        return Results.Conflict(new { message = "Ya existe una categoría con ese nombre." });

    var category = new Category { Name = name, Description = input.Description?.Trim() ?? "" };
    db.Categories.Add(category);
    await db.SaveChangesAsync();
    return Results.Created($"/api/categories/{category.Id}", category);
});

categories.MapPut("/{id:int}", async (int id, CategoryInput input, StockFacilDbContext db) =>
{
    var error = InventoryRules.Label(input.Name, "Categoría", 80)
        ?? InventoryRules.Note(input.Description, "Descripción", 300);
    if (error is not null) return Results.BadRequest(new { message = error });
    var category = await db.Categories.FindAsync(id);
    if (category is null || !category.Active)
        return Results.NotFound(new { message = "No se encontró la categoría." });

    var name = input.Name.Trim();
    if (name.Length == 0)
        return Results.BadRequest(new { message = "El nombre de la categoría es obligatorio." });
    if (await db.Categories.AnyAsync(item => item.Id != id && item.Name.ToLower() == name.ToLower()))
        return Results.Conflict(new { message = "Ya existe una categoría con ese nombre." });

    category.Name = name;
    category.Description = input.Description?.Trim() ?? "";
    await db.SaveChangesAsync();
    return Results.Ok(category);
});

categories.MapDelete("/{id:int}", async (int id, StockFacilDbContext db) =>
{
    var category = await db.Categories.FindAsync(id);
    if (category is null || !category.Active)
        return Results.NotFound(new { message = "No se encontró la categoría." });
    if (await db.Products.AnyAsync(product => product.CategoryId == id && product.Active))
        return Results.Conflict(new { message = "Mueve o desactiva primero los productos de esta categoría." });

    category.Active = false;
    await db.SaveChangesAsync();
    return Results.NoContent();
});

var products = app.MapGroup("/api/products");
products.MapGet("/", async (string? search, int? categoryId, StockFacilDbContext db) =>
{
    var query = db.Products.AsNoTracking()
        .Include(product => product.Category)
        .Where(product => product.Active);

    if (!string.IsNullOrWhiteSpace(search))
        query = query.Where(product => product.Name.Contains(search) ||
                                       product.Sku.Contains(search) ||
                                       product.Brand.Contains(search) ||
                                       product.Model.Contains(search));
    if (categoryId.HasValue)
        query = query.Where(product => product.CategoryId == categoryId.Value);

    var result = await query.OrderBy(product => product.Name)
        .Select(product => new ProductOutput(
            product.Id,
            product.CategoryId,
            product.Category!.Name,
            product.Sku,
            product.Name,
            product.Brand,
            product.Model,
            product.Price,
            product.Stock,
            product.MinimumStock,
            product.Stock <= product.MinimumStock))
        .ToListAsync();

    return Results.Ok(result);
});

products.MapGet("/{id:int}", async (int id, StockFacilDbContext db) =>
{
    var product = await db.Products.AsNoTracking()
        .Include(item => item.Category)
        .Where(item => item.Id == id && item.Active)
        .Select(item => new ProductOutput(
            item.Id, item.CategoryId, item.Category!.Name, item.Sku, item.Name,
            item.Brand, item.Model, item.Price, item.Stock, item.MinimumStock,
            item.Stock <= item.MinimumStock))
        .FirstOrDefaultAsync();
    return product is null ? Results.NotFound() : Results.Ok(product);
});

products.MapPost("/", async (ProductInput input, StockFacilDbContext db) =>
{
    string? requestId = null;
    if (input.ClientRequestId is not null)
    {
        if (!Guid.TryParse(input.ClientRequestId, out var parsedId))
            return Results.BadRequest(new { message = "El identificador del envío no es válido." });
        requestId = parsedId.ToString();
        var receipt = await db.ProductSubmissions.AsNoTracking()
            .FirstOrDefaultAsync(item => item.RequestId == requestId);
        if (receipt is not null) return Results.Ok(new { id = receipt.ProductId });
    }
    var error = ValidateProduct(input);
    if (error is not null) return Results.BadRequest(new { message = error });
    if (!await db.Categories.AnyAsync(category => category.Id == input.CategoryId && category.Active))
        return Results.BadRequest(new { message = "Selecciona una categoría activa." });
    if (await db.Products.AnyAsync(product => product.Sku == input.Sku.Trim()))
    {
        // Otro intento pudo confirmar este mismo envío desde la primera consulta.
        if (requestId is not null)
        {
            var receipt = await db.ProductSubmissions.AsNoTracking()
                .FirstOrDefaultAsync(item => item.RequestId == requestId);
            if (receipt is not null) return Results.Ok(new { id = receipt.ProductId });
        }
        return Results.Conflict(new { message = "El SKU ya está registrado." });
    }

    var product = new Product();
    ApplyProductInput(product, input);
    db.Products.Add(product);
    if (product.Stock > 0)
        db.StockMovements.Add(new StockMovement {
            Product = product, Type = "IN", Quantity = product.Stock, Notes = "Existencias iniciales al registrar el producto"
        });
    if (requestId is not null)
        db.ProductSubmissions.Add(new ProductSubmission { RequestId = requestId, Product = product });
    try
    {
        // Producto y recibo se confirman en la misma transacción de SaveChanges.
        await db.SaveChangesAsync();
    }
    catch (DbUpdateException)
    {
        db.ChangeTracker.Clear();
        if (requestId is not null)
        {
            var receipt = await db.ProductSubmissions.AsNoTracking()
                .FirstOrDefaultAsync(item => item.RequestId == requestId);
            if (receipt is not null) return Results.Ok(new { id = receipt.ProductId });
        }
        if (await db.Products.AnyAsync(item => item.Sku == input.Sku.Trim()))
            return Results.Conflict(new { message = "El SKU ya está registrado." });
        throw;
    }
    return Results.Created($"/api/products/{product.Id}", new { product.Id });
});

products.MapPut("/{id:int}", async (int id, ProductInput input, StockFacilDbContext db) =>
{
    var error = ValidateProduct(input);
    if (error is not null) return Results.BadRequest(new { message = error });

    var product = await db.Products.FirstOrDefaultAsync(item => item.Id == id && item.Active);
    if (product is null) return Results.NotFound(new { message = "No se encontró el producto." });
    if (!await db.Categories.AnyAsync(category => category.Id == input.CategoryId && category.Active))
        return Results.BadRequest(new { message = "Selecciona una categoría activa." });
    if (await db.Products.AnyAsync(item => item.Id != id && item.Sku == input.Sku.Trim()))
        return Results.Conflict(new { message = "El SKU ya está registrado." });

    // La cantidad se modifica exclusivamente mediante movimientos; nunca desde la ficha.
    if (product.Stock != input.Stock)
        return Results.Conflict(new { message = "Las existencias cambiaron o intentas modificarlas desde la ficha. Actualiza los datos y utiliza Registrar movimiento." });
    ApplyProductInput(product, input, updateStock: false);
    try { await db.SaveChangesAsync(); }
    catch (DbUpdateConcurrencyException) {
        return Results.Conflict(new { message = "El producto cambió durante la edición. Actualiza el inventario y vuelve a intentarlo." });
    }
    return Results.NoContent();
});

products.MapDelete("/{id:int}", async (int id, StockFacilDbContext db) =>
{
    var product = await db.Products.FirstOrDefaultAsync(item => item.Id == id && item.Active);
    if (product is null) return Results.NotFound();
    product.Active = false;
    await db.SaveChangesAsync();
    return Results.NoContent();
});

var movements = app.MapGroup("/api/stock-movements");
movements.MapGet("/", async (StockFacilDbContext db) =>
    Results.Ok(await db.StockMovements.AsNoTracking()
        .Include(movement => movement.Product)
        .OrderByDescending(movement => movement.CreatedAt)
        .ThenByDescending(movement => movement.Id)
        .Select(movement => new
        {
            movement.Id,
            movement.ProductId,
            ProductName = movement.Product!.Name,
            Sku = movement.Product!.Sku,
            movement.Type,
            movement.Quantity,
            CreatedAt = DateTime.SpecifyKind(movement.CreatedAt, DateTimeKind.Utc),
            movement.Notes
        })
        .ToListAsync()));

movements.MapPost("/", async (StockMovementInput input, StockFacilDbContext db) =>
{
    var type = input.Type?.Trim().ToUpperInvariant();
    if (type is not ("IN" or "OUT"))
        return Results.BadRequest(new { message = "El tipo debe ser IN (ingreso) u OUT (salida)." });
    if (input.Quantity <= 0 || input.Quantity > InventoryRules.MaxUnits)
        return Results.BadRequest(new { message = "Cantidad: ingresa un entero entre 1 y 1 000 000." });
    var noteError = InventoryRules.Note(input.Notes, "Motivo", 300);
    if (noteError is not null) return Results.BadRequest(new { message = noteError });

    var product = await db.Products.FirstOrDefaultAsync(item => item.Id == input.ProductId && item.Active);
    if (product is null) return Results.NotFound(new { message = "No se encontró el producto." });
    if (type == "OUT" && product.Stock < input.Quantity)
        return Results.Conflict(new { message = "No hay stock suficiente para registrar la salida." });
    if (type == "IN" && (long)product.Stock + input.Quantity > InventoryRules.MaxUnits)
        return Results.Conflict(new { message = "El ingreso supera el límite de 1 000 000 de unidades por producto." });

    product.Stock += type == "IN" ? input.Quantity : -input.Quantity;
    var movement = new StockMovement
    {
        ProductId = product.Id,
        Type = type,
        Quantity = input.Quantity,
        Notes = input.Notes?.Trim() ?? ""
    };
    db.StockMovements.Add(movement);
    try { await db.SaveChangesAsync(); }
    catch (DbUpdateConcurrencyException) {
        return Results.Conflict(new { message = "Las existencias cambiaron durante la operación. Actualiza el inventario y revisa la cantidad antes de volver a registrar." });
    }
    return Results.Created($"/api/stock-movements/{movement.Id}", new { movement.Id, product.Stock });
});

app.Run();

static string? ValidateProduct(ProductInput input)
{
    return InventoryRules.Product(input);
}

static void ApplyProductInput(Product product, ProductInput input, bool updateStock = true)
{
    product.CategoryId = input.CategoryId;
    product.Sku = input.Sku.Trim();
    product.Name = input.Name.Trim();
    product.Brand = input.Brand?.Trim() ?? "";
    product.Model = input.Model?.Trim() ?? "";
    product.Price = input.Price;
    if (updateStock) product.Stock = input.Stock;
    product.MinimumStock = input.MinimumStock;
}

static async Task InitializeDatabaseAsync(WebApplication app)
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<StockFacilDbContext>();
    await db.Database.EnsureCreatedAsync();

    // Actualización aditiva para instalaciones existentes que usan EnsureCreated.
    await db.Database.ExecuteSqlRawAsync("""
        CREATE TABLE IF NOT EXISTS "ProductSubmissions" (
            "RequestId" TEXT NOT NULL PRIMARY KEY,
            "ProductId" INTEGER NOT NULL,
            FOREIGN KEY ("ProductId") REFERENCES "Products" ("Id") ON DELETE RESTRICT
        );
        """);
    await db.Database.ExecuteSqlRawAsync("""
        CREATE INDEX IF NOT EXISTS "IX_ProductSubmissions_ProductId"
        ON "ProductSubmissions" ("ProductId");
        """);

    if (await db.Categories.AnyAsync()) return;
    var categories = new[]
    {
        new Category { Name = "Componentes", Description = "Piezas internas para computadoras" },
        new Category { Name = "Periféricos", Description = "Dispositivos de entrada y salida" },
        new Category { Name = "Almacenamiento", Description = "Discos, unidades SSD y memorias" }
    };
    db.Categories.AddRange(categories);
    await db.SaveChangesAsync();

    db.Products.AddRange(
        new Product { CategoryId = categories[1].Id, Sku = "DEMO-MOU-001", Name = "Mouse Gamer Nebula M120", Brand = "Nebula", Model = "M120", Price = 79.90, Stock = 18, MinimumStock = 4 },
        new Product { CategoryId = categories[1].Id, Sku = "DEMO-TEC-002", Name = "Teclado Mecánico Nova K87", Brand = "Nova", Model = "K87", Price = 159.90, Stock = 9, MinimumStock = 3 },
        new Product { CategoryId = categories[2].Id, Sku = "DEMO-SSD-003", Name = "SSD FlashCore 1 TB", Brand = "FlashCore", Model = "1 TB", Price = 289.90, Stock = 12, MinimumStock = 3 },
        new Product { CategoryId = categories[0].Id, Sku = "DEMO-RAM-004", Name = "Memoria RAM Orion DDR4 16 GB", Brand = "Orion", Model = "DDR4 16 GB", Price = 149.90, Stock = 14, MinimumStock = 4 },
        new Product { CategoryId = categories[1].Id, Sku = "DEMO-MON-005", Name = "Monitor PixelView 24 FHD", Brand = "PixelView", Model = "24 FHD", Price = 629.90, Stock = 5, MinimumStock = 2 },
        new Product { CategoryId = categories[1].Id, Sku = "DEMO-AUD-006", Name = "Audífonos Pulse H5", Brand = "Pulse", Model = "H5", Price = 119.90, Stock = 11, MinimumStock = 3 }
    );
    await db.SaveChangesAsync();
}

public sealed record CategoryInput(string Name, string? Description);
public sealed record ProductInput(
    int CategoryId,
    string Sku,
    string Name,
    string? Brand,
    string? Model,
    double Price,
    int Stock,
    int MinimumStock,
    string? ClientRequestId = null);
public sealed record ProductOutput(
    int Id,
    int CategoryId,
    string Category,
    string Sku,
    string Name,
    string Brand,
    string Model,
    double Price,
    int Stock,
    int MinimumStock,
    bool LowStock);
public sealed record StockMovementInput(int ProductId, string Type, int Quantity, string? Notes);
