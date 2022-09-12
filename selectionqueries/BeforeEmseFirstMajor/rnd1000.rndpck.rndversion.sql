CREATE OR REPLACE FUNCTION randomPackages1() RETURNS TEXT[] AS $$
DECLARE
	res TEXT[];
	coord TEXT;
	counter INT :=0;

BEGIN
WHILE counter < 1000 LOOP

	SELECT CONCAT(package_name, ':',package_versions.version) INTO coord FROM package_versions JOIN
	(SELECT * FROM packages ORDER BY RANDOM() LIMIT 1) AS p
	ON package_versions.package_id = p.id ORDER BY RANDOM() LIMIT 1;

	IF coord IS NOT NULL THEN
    	res := array_append(res, coord);
		counter := counter + 1;
	END IF;

END LOOP;
RETURN res;
END;
$$ LANGUAGE plpgsql;

select randomPackages1()