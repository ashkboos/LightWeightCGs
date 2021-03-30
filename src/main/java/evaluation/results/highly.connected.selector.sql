SELECT package_name, version FROM (SELECT
	package_versions.package_id,
	package_versions.version
FROM (
	SELECT
		deps.package_version_id dependent_id,
		Count(*)
	FROM (
		SELECT
			dependency_id,
			version_range,
			Count(*)
		FROM
			dependencies
		GROUP BY
			dependency_id,
			version_range
		ORDER BY
			Count(*)
			DESC
		LIMIT 30) popularPackages
	JOIN dependencies deps ON popularPackages.dependency_id = deps.dependency_id
		AND popularPackages.version_range = deps.version_range
	GROUP BY
		deps.package_version_id
	ORDER BY
		Count(*)
		DESC LIMIT 100) popular_dependents
JOIN package_versions ON popular_dependents.dependent_id = package_versions.id) package_version_pops
      JOIN packages
        ON package_version_pops.package_id = packages.id;
