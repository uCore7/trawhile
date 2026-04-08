-- trawhile — V2: seed root node
-- parent_id IS NULL identifies the unique root node.

INSERT INTO nodes (name, description, is_active, sort_order)
VALUES ('Root', 'Root node — top of the node tree', TRUE, 0);
