
	
SELECT
	CONCAT(package_name,':', version)
FROM (
	SELECT
		ROW_NUMBER() OVER (PARTITION BY package_name ORDER BY version DESC) AS r,
		t.*
	FROM (
	SELECT
		package_name,
		package_versions.version
	FROM (
	SELECT
		*
	FROM (
	SELECT
		package_id,
		COUNT(*)
	FROM
		package_versions
	GROUP BY
		package_id
	HAVING
		COUNT(*) > 2 ORDER BY
			RANDOM()
		LIMIT 50) rnd
	JOIN packages ON rnd.package_id = packages.id) rndPckg
JOIN package_versions ON rndPckg.id = package_versions.package_id) t) x
WHERE
	x.r <= 2;


-- 	SELECT
-- 		package_name,
-- 		package_versions.version
-- 	FROM (
-- 	SELECT
-- 		*
-- 	FROM (
-- 	SELECT
-- 		package_id,
-- 		COUNT(*)
-- 	FROM
-- 		package_versions
-- 	GROUP BY
-- 		package_id) rnd
-- 	JOIN packages ON rnd.package_id = packages.id) rndPckg
-- JOIN package_versions ON rndPckg.id = package_versions.package_id
-- WHERE package_name like 'com.aceevo:ursus-example-application'


