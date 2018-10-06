[
	{
		"cpu": ${cpu},
		"environment": [
			{
				"name": "WAL_TABLE",
				"value": ${wal_table}
			},
			{
				"name": "DEAD_LETTER_TABLE",
				"value": ${dead_letter_table}
			},
			{
				"name": "AWS_REION",
				"value": ${aws_region}
			},
			{
				"name": "PORT",
				"value": "8000"
			}
		],
		"portMappings": [
			{
				"containerPort": 8000
			}
		],
		"essential": true,
		"image": ${docker_image},
		"memory": ${memory},
		"memoryReservation": ${memory},
		"name": "illmessage-api",
		"logConfiguration": {
			"logDriver": "awslogs",
			"options": {
				"awslogs-group": ${log_group},
				"awslogs-region": ${aws_region},
				"awslogs-stream-prefix": "illmessage-api"
			}
		}
	}
]