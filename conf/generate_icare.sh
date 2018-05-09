cat NY.P.tsv | gawk -F $'\t' '$12~/^(081|047|061)$/' > NY.icare.tsv
