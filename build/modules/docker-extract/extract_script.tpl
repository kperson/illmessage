docker run --rm -v ${out_dir}:/my_export_out ${dind_mount} ${tag} cp ${container_file} /my_export_out/${output_file}
