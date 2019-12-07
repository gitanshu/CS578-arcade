import re

def get_word_count(file_name, ignore):
	words_count = {}
	f = open(file_name)
	entire_file = f.read()
	words = re.findall(r"[\w']+", entire_file)

	for word in words:
		if word in ignore or len(word) < 3:
			continue
		if word in words_count:
			words_count[word] += 1
		else:
			words_count[word] = 1

	return words_count


def main():
	keywords_count = {}
	nonkeywords_count = {}

	f = open('javaKeyWords.txt')
	ignore = f.read().split()
	

	# create dictionary of most requent words
	file_name = input("Enter the key word java file path: (eg: ./key.java) \n") 
	keywords_count = get_word_count(file_name, ignore)

	file_name = input("Enter the non key word java file path: \n") 
	nonkeywords_count = get_word_count(file_name, ignore)

	keywords_count = (dict)(sorted(keywords_count.items(), key = 
             lambda x:x[1], reverse=True))
	nonkeywords_count = (dict) (sorted(nonkeywords_count.items(), key = 
             lambda x:x[1], reverse=True))

	words_delete = []
	# if there is an over lap of key workds, remove them
	for key in nonkeywords_count:
		if key in keywords_count:
			words_delete.append(key)

	# couldn't delete in above for loop b/c dict can't change size during iteration
	for word in words_delete:
		del keywords_count[word]
		del nonkeywords_count[word]

	output_file_name = input("Enter the output file path and name for key words: \n")

	fw = open(output_file_name , 'w')
	t = 0
	for k in keywords_count.keys():
		if t < 30:
			fw.write(k + ' ')
		t += 1
	fw.close()

	output_file_name = input("Enter the output file path and name for non key words: \n")
	fw = open(output_file_name , 'w')
	t = 0
	for k in nonkeywords_count.keys(): #only want to write top 30 words
		if t < 30:
			fw.write(k + ' ')
		t += 1
	fw.close()

if __name__ == "__main__":
	main()