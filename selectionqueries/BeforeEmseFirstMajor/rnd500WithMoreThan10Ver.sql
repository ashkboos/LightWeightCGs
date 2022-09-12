SELECT package_name, package_versions.version FROM 
(SELECT * FROM
(SELECT package_id, COUNT(*) FROM package_versions GROUP BY package_id HAVING COUNT(*) > 10) rnd 
JOIN packages ON rnd.package_id = packages.id) rndPckg
JOIN package_versions on rndPckg.id = package_versions.package_id ORDER BY RANDOM() LIMIT 500