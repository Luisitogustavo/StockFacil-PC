using Npgsql;

namespace StockFacil.Api;

internal static class PostgresConnection
{
    // DATABASE_URL se introduce como secreto en Render, nunca en el repositorio.
    internal static string FromUrl(string value)
    {
        if (!Uri.TryCreate(value.Trim(), UriKind.Absolute, out var uri) ||
            uri.Scheme is not ("postgres" or "postgresql"))
            throw new InvalidOperationException("DATABASE_URL debe ser una URL PostgreSQL de Neon.");
        var credentials = uri.UserInfo.Split(':', 2);
        if (credentials.Length != 2 || uri.AbsolutePath.Length <= 1)
            throw new InvalidOperationException("DATABASE_URL debe incluir usuario, contraseña y base de datos.");
        return new NpgsqlConnectionStringBuilder
        {
            Host = uri.Host,
            Port = uri.Port > 0 ? uri.Port : 5432,
            Username = Uri.UnescapeDataString(credentials[0]),
            Password = Uri.UnescapeDataString(credentials[1]),
            Database = Uri.UnescapeDataString(uri.AbsolutePath.TrimStart('/')),
            SslMode = SslMode.VerifyFull,
            ChannelBinding = ChannelBinding.Require,
            Pooling = true,
            MaxPoolSize = 10,
            Timeout = 30,
            CommandTimeout = 30
        }.ConnectionString;
    }
}
