awk   -F'\t' '(substr($7,1,1)=="P" && $11=="CA" && match($12, /075|081|001/) ) { print $0 }'< US.txt  > CA.icare.tsv
