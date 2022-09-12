SELECT CONCAT(package_name, ':',package_versions.version) FROM
package_versions JOIN packages ON package_versions.package_id = packages.id
ORDER BY RANDOM() LIMIT 1000