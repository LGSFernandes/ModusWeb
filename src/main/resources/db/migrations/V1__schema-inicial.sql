-- ============================================
--  ModusWeb - Migration V1: Schema Inicial
-- ============================================

-- Tabela de Roles (perfis de acesso)
CREATE TABLE IF NOT EXISTS roles (
                                     id          BIGSERIAL PRIMARY KEY,
                                     name        VARCHAR(50) NOT NULL UNIQUE
    );

-- Tabela de Usuários
CREATE TABLE IF NOT EXISTS users (
                                     id              BIGSERIAL PRIMARY KEY,
                                     name            VARCHAR(150) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    bio             TEXT,
    avatar_url      VARCHAR(500),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Tabela de junção users <-> roles
CREATE TABLE IF NOT EXISTS users_roles (
                                           user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
    );

-- Tabela de Categorias de Templates
CREATE TABLE IF NOT EXISTS categories (
                                          id          BIGSERIAL PRIMARY KEY,
                                          name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon        VARCHAR(50)
    );

-- Tabela de Templates
CREATE TABLE IF NOT EXISTS templates (
                                         id              BIGSERIAL PRIMARY KEY,
                                         title           VARCHAR(255) NOT NULL,
    description     TEXT,
    price           NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    file_path       VARCHAR(500),
    preview_image   VARCHAR(500),
    tags            VARCHAR(500),
    downloads       BIGINT NOT NULL DEFAULT 0,
    approved        BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    seller_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id     BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Tabela de Pedidos
CREATE TABLE IF NOT EXISTS orders (
                                      id              BIGSERIAL PRIMARY KEY,
                                      status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(10,2) NOT NULL,
    buyer_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id     BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Seed: roles padrão
INSERT INTO roles (name) VALUES ('ROLE_USER') ON CONFLICT DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_SELLER') ON CONFLICT DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_ADMIN') ON CONFLICT DO NOTHING;

-- Seed: categorias padrão
INSERT INTO categories (name, slug, description, icon) VALUES
                                                           ('Landing Pages',   'landing-pages',   'Páginas de conversão de alta performance', 'layout'),
                                                           ('Dashboards',      'dashboards',       'Painéis administrativos e analíticos',      'bar-chart-2'),
                                                           ('E-commerce',      'ecommerce',        'Lojas virtuais e vitrines de produtos',     'shopping-cart'),
                                                           ('Portfólios',      'portfolios',       'Sites de portfólio criativos e modernos',   'briefcase'),
                                                           ('Blogs',           'blogs',            'Templates para blogs e conteúdo',           'file-text'),
                                                           ('SaaS',            'saas',             'Interfaces para aplicações SaaS',           'cloud')
    ON CONFLICT DO NOTHING;