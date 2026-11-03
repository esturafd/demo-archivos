-- Semilla del ambiente local: dos áreas y un usuario por rol, con la contraseña
-- ficticia `password` (hash bcrypt tomado de database/tests/fixtures.py).
--
-- Se ejecuta como el propietario de la base (omite RLS), después de las
-- migraciones. No crea archivos: el bucket arranca vacío para cargarlos a mano.

INSERT INTO app.areas (id, name, description) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Área A', 'Área de ejemplo A'),
  ('22222222-2222-2222-2222-222222222222', 'Área B', 'Área de ejemplo B');

INSERT INTO app.users (id, email, password_hash, name, role, area_id) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'standard.a@example.com', '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Estandar A', 'standard',  '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'standard.b@example.com', '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Estandar B', 'standard',  '22222222-2222-2222-2222-222222222222'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'head.a@example.com',     '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Jefe A',     'area_head', '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'manager@example.com',    '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Gerente',    'manager',   NULL),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'admin@example.com',      '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Admin',      'admin',     NULL);

-- El gerente gestiona el Área A.
INSERT INTO app.manager_areas (manager_id, area_id) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', '11111111-1111-1111-1111-111111111111');
